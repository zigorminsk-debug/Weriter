import pathlib

# First, update LearningManager.kt to properly accumulate heroes, places, phrases, plots
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/LearningManager.kt')
txt = p.read_text(encoding='utf-8')

# Add new files and methods for proper accumulation
old_append = '''    private fun appendTemplates(context: Context, templates: List<Pair<String, String>>, genre: String) {
        try {
            val file = getTemplatesFile(context)
            val existing = if (file.exists()) {
                try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
            } else JSONObject()

            for ((sentence, g) in templates) {
                val genreKey = if (g == "Авто") "general" else g
                val arr = existing.optJSONArray(genreKey) ?: JSONArray()
                var exists = false
                for (i in 0 until arr.length()) {
                    if (arr.optString(i) == sentence) { exists = true; break }
                }
                if (!exists) arr.put(sentence)
                while (arr.length() > 500) arr.remove(0)
                existing.put(genreKey, arr)

                val allArr = existing.optJSONArray("all") ?: JSONArray()
                var existsAll = false
                for (i in 0 until allArr.length()) {
                    if (allArr.optString(i) == sentence) { existsAll = true; break }
                }
                if (!existsAll) allArr.put(sentence)
                while (allArr.length() > 2000) allArr.remove(0)
                existing.put("all", allArr)
            }
            file.writeText(existing.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }'''

new_append = '''    private fun getHeroesFile(context: Context): File = File(getLearnedDir(context), "learned_heroes.json")
    private fun getPlacesFile(context: Context): File = File(getLearnedDir(context), "learned_places.json")
    private fun getPhrasesFile(context: Context): File = File(getLearnedDir(context), "learned_phrases.json")
    private fun getPlotsFile(context: Context): File = File(getLearnedDir(context), "learned_plots.json")

    private fun appendTemplates(context: Context, templates: List<Pair<String, String>>, genre: String) {
        try {
            val file = getTemplatesFile(context)
            val existing = if (file.exists()) {
                try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
            } else JSONObject()

            for ((sentence, g) in templates) {
                val genreKey = if (g == "Авто") "general" else g
                val arr = existing.optJSONArray(genreKey) ?: JSONArray()
                var exists = false
                for (i in 0 until arr.length()) {
                    if (arr.optString(i) == sentence) { exists = true; break }
                }
                if (!exists) arr.put(sentence)
                while (arr.length() > 500) arr.remove(0)
                existing.put(genreKey, arr)

                val allArr = existing.optJSONArray("all") ?: JSONArray()
                var existsAll = false
                for (i in 0 until allArr.length()) {
                    if (allArr.optString(i) == sentence) { existsAll = true; break }
                }
                if (!existsAll) allArr.put(sentence)
                while (allArr.length() > 2000) allArr.remove(0)
                existing.put("all", allArr)
            }
            file.writeText(existing.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Proper accumulation: heroes, places, phrases, plots
    private fun appendLearnedData(context: Context, content: String, genre: String) {
        try {
            // Extract heroes (names)
            val heroes = extractHeroesFromText(content)
            if (heroes.isNotEmpty()) {
                val file = getHeroesFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("heroes") ?: JSONArray()
                val existingSet = mutableSetOf<String>()
                for (i in 0 until arr.length()) existingSet.add(arr.optString(i).lowercase())
                for (h in heroes) {
                    if (h.lowercase() !in existingSet && h.length in 3..20) {
                        arr.put(h)
                        existingSet.add(h.lowercase())
                    }
                }
                while (arr.length() > 300) arr.remove(0)
                obj.put("heroes", arr)
                file.writeText(obj.toString())
            }
        } catch (e: Exception) {}
        try {
            // Extract places
            val places = extractPlacesFromText(content)
            if (places.isNotEmpty()) {
                val file = getPlacesFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("places") ?: JSONArray()
                val existingSet = mutableSetOf<String>()
                for (i in 0 until arr.length()) existingSet.add(arr.optString(i).lowercase())
                for (pl in places) {
                    if (pl.lowercase() !in existingSet && pl.length in 5..60) {
                        arr.put(pl)
                        existingSet.add(pl.lowercase())
                    }
                }
                while (arr.length() > 300) arr.remove(0)
                obj.put("places", arr)
                file.writeText(obj.toString())
            }
        } catch (e: Exception) {}
        try {
            // Extract phrases (verbal turns) - 2-4 word phrases that are stylistic
            val phrases = extractPhrasesFromText(content)
            if (phrases.isNotEmpty()) {
                val file = getPhrasesFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("phrases") ?: JSONArray()
                val existingSet = mutableSetOf<String>()
                for (i in 0 until arr.length()) existingSet.add(arr.optString(i).lowercase())
                for (ph in phrases) {
                    if (ph.lowercase() !in existingSet && ph.length in 5..50) {
                        arr.put(ph)
                        existingSet.add(ph.lowercase())
                    }
                }
                while (arr.length() > 500) arr.remove(0)
                obj.put("phrases", arr)
                file.writeText(obj.toString())
            }
        } catch (e: Exception) {}
        try {
            // Extract plot elements - keywords + genre patterns
            val keywords = extractKeywordsForLearning(content).take(10)
            if (keywords.isNotEmpty()) {
                val file = getPlotsFile(context)
                val obj = if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                val arr = obj.optJSONArray("plots") ?: JSONArray()
                // Store as "genre: keyword1, keyword2, ..."
                val plotEntry = "$genre: ${keywords.joinToString(", ")}"
                var exists = false
                for (i in 0 until arr.length()) if (arr.optString(i) == plotEntry) { exists = true; break }
                if (!exists) arr.put(plotEntry)
                while (arr.length() > 200) arr.remove(0)
                obj.put("plots", arr)
                file.writeText(obj.toString())
            }
        } catch (e: Exception) {}
    }

    private fun extractHeroesFromText(text: String): List<String> {
        return try {
            // Find capitalized words that are likely names (2-15 chars, Cyrillic)
            val regex = Regex("[А-ЯЁ][а-яё]{2,15}")
            val matches = regex.findAll(text).map { it.value }.toList()
            val stop = setOf("Это", "Тот", "Та", "Те", "Так", "Вот", "Там", "Тут", "Как", "Что", "Кто", "Где", "Когда", "Почему", "Книга", "Глава", "Автор")
            matches.filter { it !in stop && it.length in 3..15 }
                .groupingBy { it }.eachCount()
                .filter { it.value >= 2 } // appears at least twice
                .keys.take(10).toList()
        } catch (e: Exception) { emptyList() }
    }

    private fun extractPlacesFromText(text: String): List<String> {
        return try {
            // Look for place patterns: "в ...", "на ...", "у ...", "за ...", "под ...", "над ..."
            val placeRegex = Regex("(?:в|на|у|за|под|над|перед|между|около|возле|рядом с|внутри|снаружи)\\s+([а-яёА-ЯЁa-zA-Z0-9\\- ]{3,40})", RegexOption.IGNORE_CASE)
            val places = mutableListOf<String>()
            for (m in placeRegex.findAll(text)) {
                val place = m.groupValues[1].trim().take(50)
                if (place.length in 5..50 && !place.contains("http") && !place.contains("www")) {
                    // Filter out non-places
                    val lower = place.lowercase()
                    if (lower.contains("это") || lower.contains("тот") || lower.contains("что") || lower.contains("который")) continue
                    places.add(m.groupValues[0].trim()) // full "в городе", "на станции"
                }
            }
            places.distinct().take(20)
        } catch (e: Exception) { emptyList() }
    }

    private fun extractPhrasesFromText(text: String): List<String> {
        return try {
            // Extract stylistic phrases - 2-4 words that are connectors or descriptive
            val connectors = listOf("в тот же миг", "через минуту", "через час", "вдруг", "тем временем", "между тем", "чуть позже", "к вечеру", "на рассвете", "спустя минуту", "в этот момент", "внезапно", "однако", "потом", "затем", "сначала", "в конце концов", "в итоге", "в тот момент", "в этот день", "на следующий день")
            val found = mutableListOf<String>()
            val lower = text.lowercase()
            for (c in connectors) if (lower.contains(c)) found.add(c)
            
            // Also extract frequent 2-3 word phrases from text
            val words = text.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\\\s+")).filter { it.length > 2 }
            if (words.size > 10) {
                val bigrams = mutableMapOf<String, Int>()
                for (i in 0 until words.size - 1) {
                    val bigram = "${words[i]} ${words[i+1]}"
                    if (bigram.length in 6..30 && !bigram.contains("http")) {
                        bigrams[bigram] = (bigrams[bigram] ?: 0) + 1
                    }
                }
                val frequent = bigrams.filter { it.value >= 3 }.keys.take(10)
                found.addAll(frequent)
            }
            found.distinct().take(30)
        } catch (e: Exception) { emptyList() }
    }'''

# Replace appendTemplates with new version that includes proper accumulation
if old_append in txt:
    txt = txt.replace(old_append, new_append)
else:
    # If already replaced, ensure appendLearnedData exists
    if 'private fun appendLearnedData' not in txt:
        txt = txt.replace('    private fun appendTemplates', new_append + '\n\n    private fun appendTemplates')

# Now update learnFromText to call appendLearnedData
old_learn = '''    fun learnFromText(context: Context, title: String, authors: String, content: String, genre: String = "Авто"): LearnedBook? {
        return try {
            if (content.length < 200) return null
            // Limit content to avoid OOM
            val limited = content.take(200000)'''

new_learn = '''    fun learnFromText(context: Context, title: String, authors: String, content: String, genre: String = "Авто"): LearnedBook? {
        return try {
            if (content.length < 200) return null
            // Limit content to avoid OOM
            val limited = content.take(200000)
            // Proper accumulation of heroes, places, phrases, plots
            try { appendLearnedData(context, limited, genre) } catch (e: Exception) {}'''

if old_learn in txt:
    txt = txt.replace(old_learn, new_learn)

# Add getters for new data
old_getCount = '''    fun getAllLearnedTemplatesCount(context: Context): Int {
        return try {
            val file = getTemplatesFile(context)
            if (!file.exists()) return 0
            val text = try { file.readText() } catch (e: Exception) { return 0 }
            if (text.length > 2_000_000) { try { file.delete() } catch (e: Exception) {}; return 0 }
            val obj = try { JSONObject(text) } catch (e: Exception) { try { file.delete() } catch (ex: Exception) {}; return 0 }
            obj.optJSONArray("all")?.length() ?: 0
        } catch (e: Exception) { 0 }
    }'''

new_getters = '''    fun getAllLearnedTemplatesCount(context: Context): Int {
        return try {
            val file = getTemplatesFile(context)
            if (!file.exists()) return 0
            val text = try { file.readText() } catch (e: Exception) { return 0 }
            if (text.length > 2_000_000) { try { file.delete() } catch (e: Exception) {}; return 0 }
            val obj = try { JSONObject(text) } catch (e: Exception) { try { file.delete() } catch (ex: Exception) {}; return 0 }
            obj.optJSONArray("all")?.length() ?: 0
        } catch (e: Exception) { 0 }
    }

    fun getLearnedHeroes(context: Context, limit: Int = 50): List<String> {
        return try {
            val file = getHeroesFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("heroes") ?: return emptyList()
            (0 until minOf(arr.length(), limit)).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    fun getLearnedPlaces(context: Context, limit: Int = 50): List<String> {
        return try {
            val file = getPlacesFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("places") ?: return emptyList()
            (0 until minOf(arr.length(), limit)).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    fun getLearnedPhrases(context: Context, limit: Int = 100): List<String> {
        return try {
            val file = getPhrasesFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("phrases") ?: return emptyList()
            (0 until minOf(arr.length(), limit)).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    fun getLearnedPlots(context: Context, limit: Int = 50): List<String> {
        return try {
            val file = getPlotsFile(context)
            if (!file.exists()) return emptyList()
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return emptyList() }
            val arr = obj.optJSONArray("plots") ?: return emptyList()
            (0 until minOf(arr.length(), limit)).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }
        } catch (e: Exception) { emptyList() }
    }

    fun getAllLearnedDataCount(context: Context): Map<String, Int> {
        return try {
            mapOf(
                "templates" to getAllLearnedTemplatesCount(context),
                "heroes" to getLearnedHeroes(context, 1000).size,
                "places" to getLearnedPlaces(context, 1000).size,
                "phrases" to getLearnedPhrases(context, 1000).size,
                "plots" to getLearnedPlots(context, 1000).size,
                "books" to getLearnedBooks(context).size
            )
        } catch (e: Exception) { emptyMap() }
    }'''

if old_getCount in txt:
    txt = txt.replace(old_getCount, new_getters)
else:
    # If already has new version, ensure new getters exist
    if 'fun getLearnedHeroes' not in txt:
        txt = txt.replace('    fun getAllLearnedTemplatesCount', new_getters + '\n\n    fun getAllLearnedTemplatesCount_OLD')

p.write_text(txt, encoding='utf-8')
print("LearningManager proper accumulation added")
