import pathlib, re
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/MainActivity.kt')
txt = p.read_text(encoding='utf-8')

# Replace generateStory method to include randomSeed and genre-based learning trigger
old = """    private fun generateStory(isContinue: Boolean) {
        val inputRaw = if (isContinue && generatedStory != null) generatedStory!!.fullText else binding.etInput.text.toString().trim()
        if (inputRaw.length < 10) { Snackbar.make(binding.root, "Введите хотя бы 10 символов!", Snackbar.LENGTH_SHORT).show(); return }
        val pages = binding.sliderPages.value.toInt()
        val genreRaw = binding.actvGenre.text.toString().ifEmpty { "Авто (из текста)" }
        val style = binding.actvStyle.text.toString().ifEmpty { "Детальный" }
        val fixGrammar = binding.cbFixGrammar.isChecked
        val customHeroesText = binding.etHeroes.text.toString().trim()
        val customPlaceText = binding.etPlace.text.toString().trim()
        val bookTitle = binding.etBookTitle.text.toString().trim()
        val authorName = binding.etAuthor.text.toString().trim()
        val useLearned = binding.cbUseLearned.isChecked
        val customHeroes = if (customHeroesText.isNotEmpty()) customHeroesText.split(",").map { it.trim() }.filter { it.length >= 2 } else emptyList()
        val genre = if (genreRaw.startsWith("Авто")) detectGenre(inputRaw) else genreRaw
        binding.progress.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.btnContinue.isEnabled = false
        lifecycleScope.launch {
            val learnedTemplates = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getRelevantTemplates(this@MainActivity, extractKeywordsForRelevance(inputRaw), genre, 200) else emptyList() }
            delay(200)
            val story = withContext(Dispatchers.Default) {
                StoryGenerator.generate(StoryGenerator.GenerationConfig(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned))
            }"""

new = """    private fun generateStory(isContinue: Boolean) {
        val inputRaw = if (isContinue && generatedStory != null) generatedStory!!.fullText else binding.etInput.text.toString().trim()
        if (inputRaw.length < 10) { Snackbar.make(binding.root, "Введите хотя бы 10 символов!", Snackbar.LENGTH_SHORT).show(); return }
        val pages = binding.sliderPages.value.toInt()
        val genreRaw = binding.actvGenre.text.toString().ifEmpty { "Авто (из текста)" }
        val style = binding.actvStyle.text.toString().ifEmpty { "Детальный" }
        val fixGrammar = binding.cbFixGrammar.isChecked
        val customHeroesText = binding.etHeroes.text.toString().trim()
        val customPlaceText = binding.etPlace.text.toString().trim()
        val bookTitle = binding.etBookTitle.text.toString().trim()
        val authorName = binding.etAuthor.text.toString().trim()
        val useLearned = binding.cbUseLearned.isChecked
        val customHeroes = if (customHeroesText.isNotEmpty()) customHeroesText.split(",").map { it.trim() }.filter { it.length >= 2 } else emptyList()
        val genre = if (genreRaw.startsWith("Авто")) detectGenre(inputRaw) else genreRaw
        // Genre-based auto-learning from Flibusta if templates low
        if (useLearned && (genre == "Фантастика" || genre == "Детектив" || genre == "Боевик")) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val count = LearningManager.getAllLearnedTemplatesCount(this@MainActivity)
                    val genreCount = LearningManager.getLearnedTemplates(this@MainActivity, genre, 500).size
                    if (genreCount < 30) {
                        withContext(Dispatchers.Main) {
                            try { binding.tvInternetStatus.text = "📚 Ищу $genre на Флибусте для обучения..." } catch (e: Exception) {}
                        }
                        val queries = when (genre) {
                            "Фантастика" -> listOf("фантастика космос", "научная фантастика", "космическая фантастика", "Лукьяненко", "Стругацкий")
                            "Детектив" -> listOf("детектив", "детектив убийство", "Акунин", "детектив расследование")
                            "Боевик" -> listOf("боевик", "спецназ", "боевик триллер")
                            else -> listOf(genre)
                        }
                        val q = queries.random()
                        val books = OpdsFetcher.searchFlibusta(q, OpdsFetcher.PRIMARY_OPDS, 0)
                        val book = books.firstOrNull { it.downloadLinks.isNotEmpty() }
                        if (book != null) {
                            val text = OpdsFetcher.fetchBookText(book)
                            if (text != null && text.length > 500) {
                                LearningManager.learnFromOpdsBook(this@MainActivity, book, text)
                                withContext(Dispatchers.Main) {
                                    updateLearnedCount()
                                    try { binding.tvInternetStatus.text = "✅ Авто-обучено $genre: ${book.title.take(20)}" } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
        }
        binding.progress.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.btnContinue.isEnabled = false
        lifecycleScope.launch {
            val learnedTemplates = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getRelevantTemplates(this@MainActivity, extractKeywordsForRelevance(inputRaw), genre, 200) else emptyList() }
            val learnedTitles = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedBooks(this@MainActivity).map { it.title }.take(20) else emptyList() }
            delay(200)
            val randomSeed = System.currentTimeMillis() + inputRaw.hashCode() + (0..100000).random()
            val story = withContext(Dispatchers.Default) {
                StoryGenerator.generate(StoryGenerator.GenerationConfig(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned, learnedTitles, randomSeed))
            }"""

if old in txt:
    txt = txt.replace(old, new)
    print("generateStory replaced")
else:
    # try fuzzy
    print("old not found, trying regex")
    # find and replace via regex for first occurrence
    import re
    pattern = re.compile(r"private fun generateStory\(isContinue: Boolean\) \{.*?val story = withContext\(Dispatchers\.Default\) \{\n.*?StoryGenerator\.GenerationConfig\(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned\)\)\n.*?\}", re.DOTALL)
    m = pattern.search(txt)
    if m:
        txt = txt[:m.start()] + new + "\n            binding.progress.visibility = View.GONE\n            binding.btnGenerate.isEnabled = true\n            binding.btnContinue.isEnabled = true" + txt[m.end():]
        print("regex replaced")
    else:
        print("FAILED to find generateStory")
        # print snippet
        idx = txt.find("private fun generateStory")
        print(txt[idx:idx+2000])

p.write_text(txt, encoding='utf-8')
print("main v18 fixed")
