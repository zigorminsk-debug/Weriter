import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/MainActivity.kt')
txt = p.read_text(encoding='utf-8')

# Fix startAutoLearnFlibusta to fallback to Gutendex if Flibusta returns 0
old_auto = '''    private fun startAutoLearnFlibusta(maxBooks: Int) {
        binding.progressInternet.visibility = View.VISIBLE
        binding.tvInternetStatus.text = "Авто-обучение Флибуста: 0/$maxBooks"
        try { binding.btnAutoLearn.isEnabled = false } catch (e: Exception) {}
        lifecycleScope.launch(Dispatchers.IO) {
            var count = 0
            val queries = LearningManager.getFlibustaPopularQueries().shuffled().take(maxBooks)
            for ((idx, q) in queries.withIndex()) {
                withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Флибуста: $q (${idx+1}/$maxBooks)" }
                try {
                    val opdsBooks = OpdsFetcher.searchFlibusta(q, OpdsFetcher.PRIMARY_OPDS, 0)
                    val book = opdsBooks.firstOrNull { it.downloadLinks.isNotEmpty() } ?: continue
                    withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Загрузка: ${book.title.take(25)}" }
                    val text = OpdsFetcher.fetchBookText(book) ?: continue
                    if (text.length < 500) continue
                    val learned = LearningManager.learnFromOpdsBook(this@MainActivity, book, text)
                    if (learned != null) count++
                    delay(2000)
                } catch (e: Exception) { continue }
            }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                try { binding.btnAutoLearn.isEnabled = true } catch (e: Exception) {}
                binding.tvInternetStatus.text = "Авто-обучено: $count/$maxBooks (OPDS)"
                updateLearnedCount()
                AlertDialog.Builder(this@MainActivity).setTitle("Авто-обучение завершено").setMessage("Изучено $count книг с Флибусты 213.5.65.159").setPositiveButton("OK", null).show()
            }
        }
    }'''

new_auto = '''    private fun startAutoLearnFlibusta(maxBooks: Int) {
        binding.progressInternet.visibility = View.VISIBLE
        binding.tvInternetStatus.text = "Авто-обучение: 0/$maxBooks (Флибуста + Gutendex)"
        try { binding.btnAutoLearn.isEnabled = false } catch (e: Exception) {}
        lifecycleScope.launch(Dispatchers.IO) {
            var count = 0
            var lastError = ""
            // Сначала пробуем Флибусту
            val queries = LearningManager.getFlibustaPopularQueries().shuffled().take(maxBooks)
            for ((idx, q) in queries.withIndex()) {
                withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Флибуста: $q (${idx+1}/$maxBooks)" }
                try {
                    val opdsBooks = OpdsFetcher.searchFlibusta(q, OpdsFetcher.PRIMARY_OPDS, 0)
                    if (opdsBooks.isEmpty()) { lastError = "Флибуста пусто для $q"; continue }
                    val book = opdsBooks.firstOrNull { it.downloadLinks.isNotEmpty() } ?: continue
                    withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Загрузка: ${book.title.take(25)}" }
                    val text = OpdsFetcher.fetchBookText(book) ?: continue
                    if (text.length < 500) continue
                    val learned = LearningManager.learnFromOpdsBook(this@MainActivity, book, text)
                    if (learned != null) count++
                    delay(1500)
                } catch (e: Exception) { lastError = e.message ?: "ошибка"; continue }
            }
            // Если Флибуста дала 0, пробуем Gutendex (работает без VPN)
            if (count == 0) {
                withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Флибуста недоступна, пробую Gutendex..." }
                try {
                    val gutQueries = listOf("science fiction", "detective", "fantasy", "adventure", "Dostoevsky", "Tolstoy")
                    for (gq in gutQueries.shuffled().take(maxBooks)) {
                        withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Gutendex: $gq" }
                        try {
                            val books = InternetBookFetcher.searchBooks(gq)
                            val book = books.firstOrNull { it.downloadUrl != null } ?: continue
                            val text = InternetBookFetcher.fetchBookText(book.downloadUrl!!) ?: continue
                            if (text.length < 500) continue
                            val learned = LearningManager.learnFromText(this@MainActivity, book.title, book.authors, text, "Фантастика")
                            if (learned != null) count++
                            delay(1000)
                        } catch (e: Exception) { continue }
                    }
                } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                try { binding.btnAutoLearn.isEnabled = true } catch (e: Exception) {}
                if (count == 0) {
                    binding.tvInternetStatus.text = "Не удалось: Флибуста требует VPN. Попробуйте Gutendex поиск."
                    AlertDialog.Builder(this@MainActivity).setTitle("Авто-обучение: 0 книг").setMessage("Флибуста 213.5.65.159 недоступна (нужен VPN в BY).\\n\\nПопробуйте:\\n• Поиск Gutendex (работает без VPN)\\n• Импорт FB2/EPUB вручную\\n• Кнопка 'Обучить' на вашем тексте\\n\\nОшибка: $lastError").setPositiveButton("OK", null).setNeutralButton("Поиск Gutendex") { _, _ -> searchInternetBooks() }.show()
                } else {
                    binding.tvInternetStatus.text = "Авто-обучено: $count/$maxBooks"
                    updateLearnedCount()
                    AlertDialog.Builder(this@MainActivity).setTitle("Авто-обучение завершено").setMessage("Изучено $count книг (Флибуста + Gutendex)").setPositiveButton("OK", null).show()
                }
            }
        }
    }'''

if old_auto in txt:
    txt = txt.replace(old_auto, new_auto)
    print("autoLearn patched with fallback")
else:
    print("old_auto not found, trying to patch via regex")
    import re
    # Replace any startAutoLearnFlibusta
    txt = re.sub(r'private fun startAutoLearnFlibusta\(maxBooks: Int\) \{.*?AlertDialog\.Builder\(this@MainActivity\)\.setTitle\("Авто-обучение завершено"\)\.setMessage\("Изучено \$count книг с Флибусты 213\.5\.65\.159"\)\.setPositiveButton\("OK", null\)\.show\(\)\s*\}\s*\}\s*\}', new_auto, txt, flags=re.DOTALL)
    print("regex patch attempted")

p.write_text(txt, encoding='utf-8')
print("main v19 patched")
