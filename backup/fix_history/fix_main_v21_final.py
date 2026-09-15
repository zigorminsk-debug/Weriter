import pathlib, re
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/MainActivity.kt')
txt = p.read_text(encoding='utf-8')

# Remove duplicate startAutoLearnFlibusta - keep only first occurrence, remove second if exists
# Find all occurrences of private fun startAutoLearnFlibusta
matches = list(re.finditer(r'private fun startAutoLearnFlibusta\(maxBooks: Int\) \{', txt))
if len(matches) > 1:
    # Remove second occurrence till its closing (approx find next private fun or end)
    # Simplest: split by pattern and keep first
    parts = txt.split('private fun startAutoLearnFlibusta(maxBooks: Int) {')
    # parts[0] is before first, parts[1] is after first, parts[2] after second
    # We need to reconstruct keeping first implementation but removing second
    # Find where second implementation ends: look for next "\n    private fun " after second
    # We'll just remove everything from second occurrence to next private fun learnFromCurrentText
    txt = re.sub(r'private fun startAutoLearnFlibusta\(maxBooks: Int\) \{.*?private fun learnFromCurrentText\(\)', 'private fun learnFromCurrentText()', txt, flags=re.DOTALL)
    print("removed duplicate startAutoLearnFlibusta")

# Now replace startAutoLearnFlibusta with version that uses flibusta.net primary and Gutendex fallback
new_func = '''
    private fun startAutoLearnFlibusta(maxBooks: Int) {
        binding.progressInternet.visibility = View.VISIBLE
        binding.tvInternetStatus.text = "Авто-обучение: 0/$maxBooks (flibusta.net)"
        try { binding.btnAutoLearn.isEnabled = false } catch (e: Exception) {}
        lifecycleScope.launch(Dispatchers.IO) {
            var count = 0
            var lastError = ""
            val queries = LearningManager.getFlibustaPopularQueries().shuffled().take(maxBooks)
            for ((idx, q) in queries.withIndex()) {
                withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Флибуста: $q (${idx+1}/$maxBooks)" }
                try {
                    val opdsBooks = OpdsFetcher.searchFlibusta(q, OpdsFetcher.PRIMARY_OPDS, 0)
                    if (opdsBooks.isEmpty()) { lastError = "Пусто: $q"; continue }
                    val book = opdsBooks.firstOrNull { it.downloadLinks.isNotEmpty() } ?: continue
                    withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Загрузка: ${book.title.take(25)}" }
                    val text = OpdsFetcher.fetchBookText(book) ?: continue
                    if (text.length < 500) continue
                    val learned = LearningManager.learnFromOpdsBook(this@MainActivity, book, text)
                    if (learned != null) count++
                    delay(1500)
                } catch (e: Exception) { lastError = e.message ?: "error"; continue }
            }
            // Fallback to Gutendex if Flibusta gave 0
            if (count == 0) {
                withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Флибуста 0, пробую Gutendex..." }
                val gutQueries = listOf("science fiction", "detective", "fantasy", "adventure", "Dostoevsky", "Tolstoy").shuffled().take(maxBooks)
                for ((idx, gq) in gutQueries.withIndex()) {
                    withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Gutendex: $gq (${idx+1}/$maxBooks)" }
                    try {
                        val books = InternetBookFetcher.searchBooks(gq, "fantasy", 3)
                        val book = books.firstOrNull { it.downloadUrl != null } ?: continue
                        val text = InternetBookFetcher.fetchBookText(book) ?: continue
                        if (text.length < 500) continue
                        val learned = LearningManager.learnFromInternetBook(this@MainActivity, book, text)
                        if (learned != null) count++
                        delay(1000)
                    } catch (e: Exception) { continue }
                }
            }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                try { binding.btnAutoLearn.isEnabled = true } catch (e: Exception) {}
                if (count == 0) {
                    binding.tvInternetStatus.text = "Не удалось: ${lastError.take(50)}"
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Авто-обучение")
                        .setMessage("Флибуста flibusta.net дала 0 книг. В РБ она работает без VPN (http://flibusta.net/opds). Попробуйте:\\n• Другое слово для поиска\\n• Импорт FB2/EPUB вручную\\n• Gutendex поиск\\n\\nПоследняя ошибка: $lastError")
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Gutendex") { _, _ -> searchInternetBooks() }
                        .show()
                } else {
                    binding.tvInternetStatus.text = "Авто-обучено: $count/$maxBooks (flibusta.net + Gutendex)"
                    updateLearnedCount()
                    AlertDialog.Builder(this@MainActivity).setTitle("Авто-обучение завершено").setMessage("Изучено $count книг с flibusta.net (+ Gutendex)").setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun learnFromCurrentText() {
'''

# Replace existing startAutoLearnFlibusta with new one
txt = re.sub(r'private fun startAutoLearnFlibusta\(maxBooks: Int\) \{.*?private fun learnFromCurrentText\(\)', new_func, txt, flags=re.DOTALL)

# Clean duplicate messages
txt = txt.replace('flibusta.net/opds - Флибуста (работает в РБ) (работает в РБ)', 'flibusta.net/opds - Флибуста (работает в РБ)')
txt = txt.replace('Изучить 3 популярных книг с Флибусты OPDS flibusta.net?\nТребуется интернет, возможно VPN.', 'Изучить 3 популярных книг с Флибусты flibusta.net? В РБ работает без VPN.')
txt = txt.replace('Изучить 3 популярных книг с Флибусты flibusta.net?', 'Изучить 3 популярных книг с Флибусты flibusta.net? В РБ работает без VPN.')
# Ensure title is clean
txt = txt.replace('Авто-обучение Флибуста flibusta.net', 'Авто-обучение flibusta.net')

# Fix statistics message
txt = txt.replace('📊 Статистика v21 OPDS flibusta.net', '📊 Статистика v21 flibusta.net')

p.write_text(txt, encoding='utf-8')
print("main v21 final fixed with fallback")
