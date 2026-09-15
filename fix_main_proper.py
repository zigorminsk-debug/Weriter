import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/MainActivity.kt')
txt = p.read_text(encoding='utf-8')

old_update = """    private fun updateLearnedCount() {
        try {
            if (isFinishing || isDestroyed) return
            lifecycleScope.launch(Dispatchers.IO) {
                val count = try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (e: Exception) { 0 }
                val books = try { LearningManager.getLearnedBooks(this@MainActivity).size } catch (e: Exception) { 0 }
                withContext(Dispatchers.Main) {
                    try {
                        if (isFinishing || isDestroyed) return@withContext
                        binding.chipLearnedCount.text = "$count фраз • $books книг • OPDS flibusta.net"
                    } catch (e: Exception) {
                        try { binding.chipLearnedCount.text = "0 фраз • 0 книг • OPDS flibusta.net" } catch (ex: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            try { binding.chipLearnedCount.text = "0 фраз • 0 книг • OPDS flibusta.net" } catch (ex: Exception) {}
        }
    }"""

new_update = """    private fun updateLearnedCount() {
        try {
            if (isFinishing || isDestroyed) return
            lifecycleScope.launch(Dispatchers.IO) {
                val count = try { LearningManager.getAllLearnedDataCount(this@MainActivity) } catch (e: Exception) {
                    try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (ex: Exception) { 0 }
                }
                val templates = try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (e: Exception) { 0 }
                val heroes = try { LearningManager.getLearnedHeroes(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                val places = try { LearningManager.getLearnedPlaces(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                val phrases = try { LearningManager.getLearnedPhrases(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                val books = try { LearningManager.getLearnedBooks(this@MainActivity).size } catch (e: Exception) { 0 }
                withContext(Dispatchers.Main) {
                    try {
                        if (isFinishing || isDestroyed) return@withContext
                        binding.chipLearnedCount.text = "$count данных ($templates фр, $heroes гер, $places мест, $phrases обор) • $books книг • OPDS flibusta.net"
                    } catch (e: Exception) {
                        try { binding.chipLearnedCount.text = "0 фраз • 0 книг • OPDS flibusta.net" } catch (ex: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            try { binding.chipLearnedCount.text = "0 фраз • 0 книг • OPDS flibusta.net" } catch (ex: Exception) {}
        }
    }"""

txt = txt.replace(old_update, new_update)

# Update generateStory to fetch learned heroes/places/phrases
old_gen = """        lifecycleScope.launch {
            val learnedTemplates = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getRelevantTemplates(this@MainActivity, extractKeywordsForRelevance(inputRaw), genre, 200) else emptyList() }
            delay(200)
            val story = withContext(Dispatchers.Default) {
                StoryGenerator.generate(StoryGenerator.GenerationConfig(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned))
            }"""

new_gen = """        lifecycleScope.launch {
            val learnedTemplates = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getRelevantTemplates(this@MainActivity, extractKeywordsForRelevance(inputRaw), genre, 200) else emptyList() }
            val learnedHeroes = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedHeroes(this@MainActivity, 100) else emptyList() }
            val learnedPlaces = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPlaces(this@MainActivity, 100) else emptyList() }
            val learnedPhrases = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPhrases(this@MainActivity, 100) else emptyList() }
            delay(200)
            val story = withContext(Dispatchers.Default) {
                StoryGenerator.generate(StoryGenerator.GenerationConfig(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned, learnedHeroes, learnedPlaces, learnedPhrases))
            }"""

txt = txt.replace(old_gen, new_gen)

# Update stats dialog
old_stats = """            val stats = LearningManager.getStats(this@MainActivity)
            val templatesCount = LearningManager.getAllLearnedTemplatesCount(this@MainActivity)
            withContext(Dispatchers.Main) {
                val totalBooks = stats.optInt("totalBooks", 0)
                val totalChars = stats.optInt("totalChars", 0)
                val message = "📊 Статистика v13 OPDS\\n\\n📚 Книг: $totalBooks\\n📝 Знаков: $totalChars\\n🧠 Шаблонов: $templatesCount\\n\\nИсточники:\\n• 🔥 flibusta.net/opds - Флибуста (работает в РБ)\\n• Gutendex 70k+\\n• OpenLibrary 40M+\""""

new_stats = """            val stats = LearningManager.getStats(this@MainActivity)
            val templatesCount = LearningManager.getAllLearnedTemplatesCount(this@MainActivity)
            val heroesCount = try { LearningManager.getLearnedHeroes(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val placesCount = try { LearningManager.getLearnedPlaces(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val phrasesCount = try { LearningManager.getLearnedPhrases(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val plotsCount = try { LearningManager.getLearnedPlots(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val totalData = try { LearningManager.getAllLearnedDataCount(this@MainActivity) } catch (e: Exception) { templatesCount }
            withContext(Dispatchers.Main) {
                val totalBooks = stats.optInt("totalBooks", 0)
                val totalChars = stats.optInt("totalChars", 0)
                val message = "📊 Статистика v26 PROPER\\n\\n📚 Книг: $totalBooks\\n📝 Знаков: $totalChars\\n🧠 Всего данных: $totalData\\n• Шаблонов: $templatesCount\\n• Героев: $heroesCount\\n• Мест: $placesCount\\n• Оборотов: $phrasesCount\\n• Сюжетов: $plotsCount\\n\\n✅ Правильное обучение: герои, места, обороты, сюжеты накапливаются и используются для генерации СВОЕГО текста, а не вставки чужих отрывков!\\n\\nИсточники:\\n• 🔥 flibusta.net/opds - Флибуста (работает в РБ)\\n• Gutendex 70k+\\n• OpenLibrary 40M+\""""

txt = txt.replace(old_stats, new_stats)

p.write_text(txt, encoding='utf-8')
print("MainActivity proper patched")
