import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/LearningManager.kt')
txt = p.read_text(encoding='utf-8')

# Add new file getters if not present
if 'getHeroesFile' not in txt:
    txt = txt.replace(
        '    private fun getStatsFile(context: Context): File {\n        return File(getLearnedDir(context), "learning_stats.json")\n    }',
        '''    private fun getStatsFile(context: Context): File {
        return File(getLearnedDir(context), "learning_stats.json")
    }

    private fun getHeroesFile(context: Context): File {
        return File(getLearnedDir(context), "learned_heroes.json")
    }

    private fun getPlacesFile(context: Context): File {
        return File(getLearnedDir(context), "learned_places.json")
    }

    private fun getPhrasesFile(context: Context): File {
        return File(getLearnedDir(context), "learned_phrases.json")
    }

    private fun getPlotsFile(context: Context): File {
        return File(getLearnedDir(context), "learned_plots.json")
    }'''
    )

# Replace learnFromText with version that calls appendLearnedData
old_learn = '''    fun learnFromText(context: Context, title: String, authors: String, content: String, genre: String = "Авто"): LearnedBook? {
        return try {
            if (content.length < 200) return null
            // Limit content to avoid OOM
            val limited = content.take(200000)
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val safeTitle = title.replace(Regex("[^a-zA-Zа-яА-Я0-9]"), "_").take(20).ifBlank { "book" }
            val fileName = "${safeTitle}_${sdf.format(Date())}.txt"
            val booksDir = try { getBooksDir(context) } catch (e: Exception) { return null }
            val file = File(booksDir, fileName)
            try { file.writeText(limited) } catch (e: Exception) { return null }

            val keywords = try { extractKeywordsForLearning(limited) } catch (e: Exception) { emptyList() }
            val templates = try { extractTemplates(limited, genre) } catch (e: Exception) { emptyList() }
            try { appendTemplates(context, templates, genre) } catch (e: Exception) {}
            try { updateStats(context, limited.length, templates.size) } catch (e: Exception) {}

            val learned = LearnedBook(
                title = title.take(100),
                authors = authors.take(100),
                date = System.currentTimeMillis(),
                chars = limited.length,
                genre = genre.take(20),
                keywords = keywords.take(10),
                path = file.absolutePath
            )
            try { saveBookIndex(context, learned) } catch (e: Exception) {}
            learned
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }'''

new_learn = '''    fun learnFromText(context: Context, title: String, authors: String, content: String, genre: String = "Авто"): LearnedBook? {
        return try {
            if (content.length < 200) return null
            val limited = content.take(200000)
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val safeTitle = title.replace(Regex("[^a-zA-Zа-яА-Я0-9]"), "_").take(20).ifBlank { "book" }
            val fileName = "${safeTitle}_${sdf.format(Date())}.txt"
            val booksDir = try { getBooksDir(context) } catch (e: Exception) { return null }
            val file = File(booksDir, fileName)
            try { file.writeText(limited) } catch (e: Exception) { return null }

            val keywords = try { extractKeywordsForLearning(limited) } catch (e: Exception) { emptyList() }
            val templates = try { extractTemplates(limited, genre) } catch (e: Exception) { emptyList() }
            try { appendTemplates(context, templates, genre) } catch (e: Exception) {}
            try { appendLearnedData(context, limited) } catch (e: Exception) { e.printStackTrace() }
            try { updateStats(context, limited.length, templates.size) } catch (e: Exception) {}

            val learned = LearnedBook(
                title = title.take(100),
                authors = authors.take(100),
                date = System.currentTimeMillis(),
                chars = limited.length,
                genre = genre.take(20),
                keywords = keywords.take(10),
                path = file.absolutePath
            )
            try { saveBookIndex(context, learned) } catch (e: Exception) {}
            learned
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun appendLearnedData(context: Context, text: String) {
        try {
            val heroes = extractHeroesFromText(text)
            val places = extractPlacesFromText(text)
            val phrases = extractPhrasesFromText(text)
            val plotKeywords = extractKeywordsForLearning(text).take(20)

            if (heroes.isNotEmpty()) {
                val file = getHeroesFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("heroes") ?: JSONArray()
                val existing = (0 until arr.length()).map { arr.optString(it).lowercase() }.toSet()
                for (h in heroes) {
                    if (h.lowercase() !in existing && h.length in 2..30) {
                        arr.put(h)
                    }
                }
                while (arr.length() > 500) arr.remove(0)
                obj.put("heroes", arr)
                file.writeText(obj.toString())
            }

            if (places.isNotEmpty()) {
                val file = getPlacesFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("places") ?: JSONArray()
                val existing = (0 until arr.length()).map { arr.optString(it).lowercase() }.toSet()
                for (pl in places) {
                    if (pl.lowercase() !in existing && pl.length in 3..80) {
                        arr.put(pl)
                    }
                }
                while (arr.length() > 500) arr.remove(0)
                obj.put("places", arr)
                file.writeText(obj.toString())
            }

            if (phrases.isNotEmpty()) {
                val file = getPhrasesFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("phrases") ?: JSONArray()
                val existing = (0 until arr.length()).map { arr.optString(it).lowercase() }.toSet()
                for (ph in phrases) {
                    if (ph.lowercase() !in existing && ph.length in 3..100) {
                        arr.put(ph)
                    }
                }
                while (arr.length() > 800) arr.remove(0)
                obj.put("phrases", arr)
                file.writeText(obj.toString())
            }

            if (plotKeywords.isNotEmpty()) {
                val file = getPlotsFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("plots") ?: JSONArray()
                val plotStr = plotKeywords.joinToString(", ")
                var exists = false
                for (i in 0 until arr.length()) { if (arr.optString(i) == plotStr) { exists = true; break } }
                if (!exists) arr.put(plotStr)
                while (arr.length() > 300) arr.remove(0)
                obj.put("plots", arr)
                file.writeText(obj.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractHeroesFromText(text: String): List<String> {
        return try {
            // Support both Russian and English names, frequency >=1 to catch more
            val capRu = Regex("[А-ЯЁ][а-яё]{2,20}").findAll(text).map { it.value }.toList()
            val capEn = Regex("[A-Z][a-z]{2,20}").findAll(text).map { it.value }.toList()
            val allCaps = capRu + capEn
            val freq = allCaps.groupingBy { it }.eachCount()
            val stopRu = setOf("Это", "Тот", "Та", "Там", "Тут", "Как", "Что", "Кто", "Где", "Когда", "Почему", "Он", "Она", "Они", "Мы", "Вы", "Город", "Дом", "Мир", "Жизнь", "Время", "Весь", "Вся", "Вечер", "Утро", "День", "Ночь", "Очень", "Даже", "Уже", "Только", "Был", "Была", "Были", "Будет", "Есть", "Этот", "Эта")
            val stopEn = setOf("The", "This", "That", "There", "Here", "What", "When", "Where", "Why", "How", "And", "But", "For", "With", "From", "They", "She", "You", "Not", "Are", "Was", "Were", "Have", "Has", "Had", "Will", "Would", "Could", "Should")
            freq.filter { it.value >= 2 && it.key !in stopRu && it.key !in stopEn && it.key.length in 3..20 }
                .entries.sortedByDescending { it.value }
                .map { it.key }
                .take(30)
                .ifEmpty {
                    // Fallback: take most frequent even if appears once, to ensure we get something
                    freq.filter { it.key !in stopRu && it.key !in stopEn && it.key.length in 3..20 }
                        .entries.sortedByDescending { it.value }
                        .map { it.key }
                        .take(10)
                }
        } catch (e: Exception) { emptyList() }
    }

    private fun extractPlacesFromText(text: String): List<String> {
        return try {
            val places = mutableListOf<String>()
            // Russian places
            val placeRegexRu = Regex("""(?:в|на|у|за|под|над|перед|между|около|возле|рядом с|внутри|среди)\s+[а-яёA-ЯЁa-zA-Z\-]{3,20}(?:\s+[а-яёA-ЯЁa-zA-Z\-]{2,20}){0,2}""", RegexOption.IGNORE_CASE)
            places.addAll(placeRegexRu.findAll(text).map { it.value.trim().replace(Regex("""\s+"""), " ").take(80) }.filter { it.length in 4..80 })
            // English places
            val placeRegexEn = Regex("""(?:in|on|at|near|behind|under|over|between|inside|outside|beside)\s+[A-Za-z\-]{3,20}(?:\s+[A-Za-z\-]{2,20}){0,2}""", RegexOption.IGNORE_CASE)
            places.addAll(placeRegexEn.findAll(text).map { it.value.trim().replace(Regex("""\s+"""), " ").take(80) }.filter { it.length in 4..80 })
            // Also extract frequent location-like nouns: words like "дом", "город", "улица", "комната" etc with preposition context
            val freq = places.groupingBy { it.lowercase() }.eachCount()
            freq.entries.sortedByDescending { it.value }.map { it.key }.take(50).distinct().take(30)
        } catch (e: Exception) { emptyList() }
    }

    private fun extractPhrasesFromText(text: String): List<String> {
        return try {
            val connectorsRu = listOf(
                "тем временем", "между тем", "в тот же миг", "вдруг", "однако", "потом", "через час",
                "к вечеру", "на рассвете", "спустя минуту", "чуть позже", "в конце концов",
                "несмотря на", "вопреки", "вместо того", "к тому же", "при этом", "кстати",
                "по правде говоря", "честно говоря", "как оказалось", "как назло", "к счастью",
                "к сожалению", "на самом деле", "в самом деле", "в итоге", "в целом",
                "одним словом", "другими словами", "иными словами", "например", "к примеру",
                "внезапно", "неожиданно", "медленно", "быстро", "тихо", "громко"
            )
            val connectorsEn = listOf(
                "meanwhile", "however", "therefore", "moreover", "suddenly", "later", "after that",
                "in the end", "for example", "in fact", "actually", "finally", "then", "afterwards"
            )
            val found = mutableListOf<String>()
            val lower = text.lowercase()
            for (c in connectorsRu + connectorsEn) {
                if (lower.contains(c)) found.add(c)
            }
            // Extract frequent bigrams/trigrams
            val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.length in 10..200 }
            val bigrams = mutableMapOf<String, Int>()
            for (s in sentences.take(300)) {
                val words = s.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("""\s+""")).filter { it.length > 2 }
                for (i in 0 until words.size-1) {
                    val bg = "${words[i]} ${words[i+1]}"
                    if (bg.length in 4..40 && !bg.contains(Regex("""\d"""))) {
                        bigrams[bg] = (bigrams[bg] ?: 0) + 1
                    }
                }
            }
            val frequent = bigrams.filter { it.value >= 2 }.entries.sortedByDescending { it.value }.map { it.key }.take(50)
            (found + frequent).distinct().take(80)
        } catch (e: Exception) { emptyList() }
    }'''

if old_learn in txt:
    txt = txt.replace(old_learn, new_learn)
    print("learnFromText replaced")
else:
    print("old_learn not found, trying alternative")
    # Try to find function and replace manually
    import re
    # Replace up to saveBookIndex
    pass

# Now add getters and migration after getAllLearnedTemplatesCount
old_count = '''    fun getAllLearnedTemplatesCount(context: Context): Int {
        return try {
            val file = getTemplatesFile(context)
            if (!file.exists()) return 0
            val text = try { file.readText() } catch (e: Exception) { return 0 }
            if (text.length > 2_000_000) { try { file.delete() } catch (e: Exception) {}; return 0 }
            val obj = try { JSONObject(text) } catch (e: Exception) { try { file.delete() } catch (ex: Exception) {}; return 0 }
            obj.optJSONArray("all")?.length() ?: 0
        } catch (e: Exception) { 0 }
    }'''

new_count = '''    fun getAllLearnedTemplatesCount(context: Context): Int {
        return try {
            val file = getTemplatesFile(context)
            if (!file.exists()) return 0
            val text = try { file.readText() } catch (e: Exception) { return 0 }
            if (text.length > 2_000_000) { try { file.delete() } catch (e: Exception) {}; return 0 }
            val obj = try { JSONObject(text) } catch (e: Exception) { try { file.delete() } catch (ex: Exception) {}; return 0 }
            obj.optJSONArray("all")?.length() ?: 0
        } catch (e: Exception) { 0 }
    }

    fun getLearnedHeroes(context: Context, limit: Int = 100): List<String> {
        return try {
            val file = getHeroesFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("heroes") ?: return emptyList()
            (0 until arr.length()).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }.shuffled().take(limit)
        } catch (e: Exception) { emptyList() }
    }

    fun getLearnedPlaces(context: Context, limit: Int = 100): List<String> {
        return try {
            val file = getPlacesFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("places") ?: return emptyList()
            (0 until arr.length()).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }.shuffled().take(limit)
        } catch (e: Exception) { emptyList() }
    }

    fun getLearnedPhrases(context: Context, limit: Int = 100): List<String> {
        return try {
            val file = getPhrasesFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("phrases") ?: return emptyList()
            (0 until arr.length()).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }.shuffled().take(limit)
        } catch (e: Exception) { emptyList() }
    }

    fun getLearnedPlots(context: Context, limit: Int = 50): List<String> {
        return try {
            val file = getPlotsFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("plots") ?: return emptyList()
            (0 until arr.length()).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }.shuffled().take(limit)
        } catch (e: Exception) { emptyList() }
    }

    fun getAllLearnedDataCount(context: Context): Int {
        return try {
            getAllLearnedTemplatesCount(context) + getLearnedHeroes(context, 1000).size + getLearnedPlaces(context, 1000).size + getLearnedPhrases(context, 1000).size + getLearnedPlots(context, 1000).size
        } catch (e: Exception) { getAllLearnedTemplatesCount(context) }
    }

    fun reprocessExistingBooks(context: Context): Int {
        return try {
            var count = 0
            val booksDir = getBooksDir(context)
            if (!booksDir.exists()) return 0
            val files = booksDir.listFiles()?.filter { it.isFile && it.length() > 200 } ?: return 0
            for (f in files.take(100)) {
                try {
                    val text = f.readText().take(200000)
                    if (text.length < 200) continue
                    appendLearnedData(context, text)
                    count++
                } catch (e: Exception) { continue }
            }
            count
        } catch (e: Exception) { 0 }
    }

    fun clearAndRebuildFromBooks(context: Context): Int {
        return try {
            // Clear heroes/places/phrases/plots and rebuild from all books
            try { getHeroesFile(context).delete() } catch (e: Exception) {}
            try { getPlacesFile(context).delete() } catch (e: Exception) {}
            try { getPhrasesFile(context).delete() } catch (e: Exception) {}
            try { getPlotsFile(context).delete() } catch (e: Exception) {}
            reprocessExistingBooks(context)
        } catch (e: Exception) { 0 }
    }'''

if old_count in txt:
    txt = txt.replace(old_count, new_count)
    print("getters added")
else:
    print("old_count not found")
    if 'fun getLearnedHeroes' not in txt:
        txt = txt.replace('    private fun saveBookIndex', new_count + '\n\n    private fun saveBookIndex')
        print("inserted before saveBookIndex")

p.write_text(txt, encoding='utf-8')
print("done")
