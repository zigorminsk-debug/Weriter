import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/MainActivity.kt')
txt = p.read_text(encoding='utf-8')

# Update updateLearnedCount to auto-rebuild if heroes/places/phrases are 0 but templates exist
old_update = '''    private fun updateLearnedCount() {
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
    }'''

new_update = '''    private fun updateLearnedCount() {
        try {
            if (isFinishing || isDestroyed) return
            lifecycleScope.launch(Dispatchers.IO) {
                var count = try { LearningManager.getAllLearnedDataCount(this@MainActivity) } catch (e: Exception) {
                    try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (ex: Exception) { 0 }
                }
                var templates = try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (e: Exception) { 0 }
                var heroes = try { LearningManager.getLearnedHeroes(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                var places = try { LearningManager.getLearnedPlaces(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                var phrases = try { LearningManager.getLearnedPhrases(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                val books = try { LearningManager.getLearnedBooks(this@MainActivity).size } catch (e: Exception) { 0 }

                // Auto-rebuild if old data: 691 фраз, 0 гер, 0 мест, 0 обор - значит старая база, нужно пересобрать
                if (templates > 0 && heroes == 0 && places == 0 && phrases == 0 && books > 0) {
                    try {
                        val rebuilt = LearningManager.reprocessExistingBooks(this@MainActivity)
                        // recount after rebuild
                        templates = try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (e: Exception) { templates }
                        heroes = try { LearningManager.getLearnedHeroes(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                        places = try { LearningManager.getLearnedPlaces(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                        phrases = try { LearningManager.getLearnedPhrases(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                        count = try { LearningManager.getAllLearnedDataCount(this@MainActivity) } catch (e: Exception) { count }
                    } catch (e: Exception) {}
                }

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
    }

    private fun rebuildLearnedData() {
        AlertDialog.Builder(this).setTitle("🔧 Пересобрать базу?")
            .setMessage("Пересобрать героев, места, обороты из уже изученных 24 книг?\\n\\nЭто исправит 0 гер, 0 мест, 0 обор.")
            .setPositiveButton("Пересобрать") { _, _ ->
                binding.progressInternet.visibility = View.VISIBLE
                binding.tvInternetStatus.text = "Пересборка базы..."
                lifecycleScope.launch(Dispatchers.IO) {
                    val rebuilt = try { LearningManager.clearAndRebuildFromBooks(this@MainActivity) } catch (e: Exception) { 0 }
                    withContext(Dispatchers.Main) {
                        binding.progressInternet.visibility = View.GONE
                        updateLearnedCount()
                        Toast.makeText(this@MainActivity, "Пересобрано из $rebuilt книг", Toast.LENGTH_LONG).show()
                        showLearningStats()
                    }
                }
            }
            .setNegativeButton("Отмена", null).show()
    }'''

txt = txt.replace(old_update, new_update)

# Update showLearningStats to add rebuild and clear buttons
old_stats = '''            withContext(Dispatchers.Main) {
                val totalBooks = stats.optInt("totalBooks", 0)
                val totalChars = stats.optInt("totalChars", 0)
                val message = "📊 Статистика v26 PROPER\\n\\n📚 Книг: $totalBooks\\n📝 Знаков: $totalChars\\n🧠 Всего данных: $totalData\\n• Шаблонов: $templatesCount\\n• Героев: $heroesCount\\n• Мест: $placesCount\\n• Оборотов: $phrasesCount\\n• Сюжетов: $plotsCount\\n\\n✅ Правильное обучение: герои, места, обороты, сюжеты накапливаются и используются для генерации СВОЕГО текста, а не вставки чужих отрывков!\\n\\nИсточники:\\n• 🔥 flibusta.net/opds - Флибуста (работает в РБ)\\n• Gutendex 70k+\\n• OpenLibrary 40M+"
                AlertDialog.Builder(this@MainActivity).setTitle("Статистика OPDS").setMessage(message).setPositiveButton("OK", null).setNeutralButton("Авто Флибуста") { _, _ -> autoLearnFlibusta() }.show()
            }'''

new_stats = '''            withContext(Dispatchers.Main) {
                val totalBooks = stats.optInt("totalBooks", 0)
                val totalChars = stats.optInt("totalChars", 0)
                val message = "📊 Статистика v27 FIXED\\n\\n📚 Книг: $totalBooks\\n📝 Знаков: $totalChars\\n🧠 Всего данных: $totalData\\n• Шаблонов: $templatesCount\\n• Героев: $heroesCount\\n• Мест: $placesCount\\n• Оборотов: $phrasesCount\\n• Сюжетов: $plotsCount\\n\\n✅ v27 Исправлено:\\n• НЕТ вставок чужих отрывков!\\n• Только СВОЙ текст на основе героев/мест/оборотов\\n• Авто-пересборка старой базы (691 фр, 0 гер -> теперь норм)\\n\\nИсточники:\\n• 🔥 flibusta.net/opds - Флибуста (работает в РБ)\\n• Gutendex 70k+\\n• OpenLibrary 40M+"
                AlertDialog.Builder(this@MainActivity).setTitle("Статистика OPDS v27").setMessage(message)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("🔧 Пересобрать") { _, _ -> rebuildLearnedData() }
                    .setNegativeButton("Авто Флибуста") { _, _ -> autoLearnFlibusta() }
                    .show()
            }'''

txt = txt.replace(old_stats, new_stats)

# Also update generateStory to NOT use learnedTemplates for insertion (pass empty to avoid bloat)
old_gen = '''        lifecycleScope.launch {
            val learnedTemplates = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getRelevantTemplates(this@MainActivity, extractKeywordsForRelevance(inputRaw), genre, 200) else emptyList() }
            val learnedHeroes = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedHeroes(this@MainActivity, 100) else emptyList() }
            val learnedPlaces = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPlaces(this@MainActivity, 100) else emptyList() }
            val learnedPhrases = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPhrases(this@MainActivity, 100) else emptyList() }
            delay(200)
            val story = withContext(Dispatchers.Default) {
                StoryGenerator.generate(StoryGenerator.GenerationConfig(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned, learnedHeroes, learnedPlaces, learnedPhrases))
            }'''

new_gen = '''        lifecycleScope.launch {
            // v27: learnedTemplates больше НЕ используются для вставки чужих отрывков! Только герои/места/обороты
            val learnedTemplates = withContext(Dispatchers.IO) { emptyList<String>() }
            val learnedHeroes = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedHeroes(this@MainActivity, 150) else emptyList() }
            val learnedPlaces = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPlaces(this@MainActivity, 150) else emptyList() }
            val learnedPhrases = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPhrases(this@MainActivity, 150) else emptyList() }
            delay(200)
            val story = withContext(Dispatchers.Default) {
                StoryGenerator.generate(StoryGenerator.GenerationConfig(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned, learnedHeroes, learnedPlaces, learnedPhrases))
            }'''

txt = txt.replace(old_gen, new_gen)

p.write_text(txt, encoding='utf-8')
print("MainActivity v27b fixed")
