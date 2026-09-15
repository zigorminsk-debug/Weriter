package com.zakharevich.ziwriter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LearningManager {

    data class LearnedBook(
        val title: String,
        val authors: String,
        val date: Long,
        val chars: Int,
        val genre: String,
        val keywords: List<String>,
        val path: String
    )

    private fun getLearnedDir(context: Context): File {
        val dir = File(context.filesDir, "learned")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getBooksDir(context: Context): File {
        val dir = File(getLearnedDir(context), "books")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTemplatesFile(context: Context): File {
        return File(getLearnedDir(context), "learned_templates.json")
    }

    private fun getStatsFile(context: Context): File {
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
    }

    private fun getPlotsDetailedFile(context: Context): File {
        return File(getLearnedDir(context), "learned_plots_detailed.json")
    }

    private fun getGenrePlotsFile(context: Context, genre: String): File {
        val safeGenre = genre.replace(Regex("[^a-zA-Zа-яА-Я0-9]"), "_").take(20)
        return File(getLearnedDir(context), "plots_${safeGenre}.json")
    }

    fun learnFromText(context: Context, title: String, authors: String, content: String, genre: String = "Авто"): LearnedBook? {
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
            try { appendLearnedData(context, limited, genre) } catch (e: Exception) { e.printStackTrace() }
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
            try { saveDetailedPlot(context, learned) } catch (e: Exception) {}
            learned
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun appendLearnedData(context: Context, text: String, genre: String = "general") {
        try {
            val heroes = extractHeroesFromText(text)
            val places = extractPlacesFromText(text)
            val phrases = extractPhrasesFromText(text)
            val plotKeywords = extractKeywordsForLearning(text).take(20)

            // Save genre-specific plots as well
            if (plotKeywords.isNotEmpty() && genre.isNotBlank()) {
                try {
                    val genreFile = getGenrePlotsFile(context, genre)
                    val obj = if (genreFile.exists()) try { JSONObject(genreFile.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
                    val arr = obj.optJSONArray("plots") ?: JSONArray()
                    val plotStr = plotKeywords.joinToString(", ")
                    var exists = false
                    for (i in 0 until arr.length()) { if (arr.optString(i) == plotStr) { exists = true; break } }
                    if (!exists) arr.put(plotStr)
                    while (arr.length() > 100) arr.remove(0)
                    obj.put("plots", arr)
                    genreFile.writeText(obj.toString())
                } catch (e: Exception) {}
            }


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
            val placeRegexRu = Regex("""(?:в|на|у|за|под|над|перед|между|около|возле|рядом с|внутри|среди)\s+[а-яёA-ЯЁa-zA-Z\-]{3,20}(?:\s+[а-яёA-ЯЁa-zA-Z\-]{2,20}){0,2}""", RegexOption.IGNORE_CASE)
            places.addAll(placeRegexRu.findAll(text).map { it.value.trim().replace(Regex("""\s+"""), " ").take(80) }.filter { it.length in 4..80 })
            val placeRegexEn = Regex("""(?:in|on|at|near|behind|under|over|between|inside|outside|beside)\s+[A-Za-z\-]{3,20}(?:\s+[A-Za-z\-]{2,20}){0,2}""", RegexOption.IGNORE_CASE)
            places.addAll(placeRegexEn.findAll(text).map { it.value.trim().replace(Regex("""\s+"""), " ").take(80) }.filter { it.length in 4..80 })
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
    }

    private fun extractKeywordsForLearning(text: String): List<String> {
        val words = text.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 4 }
        val freq = words.groupingBy { it }.eachCount()
        return freq.entries.sortedByDescending { it.value }.map { it.key }.take(15)
    }

    private fun extractTemplates(text: String, genre: String): List<Pair<String, String>> {
        return try {
            if (text.length > 200000) return emptyList()
            val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.length in 30..250 }
            if (sentences.size > 1000) return emptyList()
            val good = sentences.filter { sentence ->
                try {
                    !sentence.contains("http") && !sentence.contains("www") &&
                    !sentence.contains("@") &&
                    sentence.count { it.isLetter() } > sentence.length * 0.5 &&
                    sentence.split(" ").size in 4..25 &&
                    !sentence.contains("�") // broken encoding
                } catch (e: Exception) { false }
            }.shuffled().take(30)
            good.map { it to genre }
        } catch (e: Exception) { emptyList() }
    }

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

    fun getLearnedTemplates(context: Context, genre: String = "general", limit: Int = 100): List<String> {
        return try {
            val file = getTemplatesFile(context)
            if (!file.exists()) return emptyList()
            val text = try { file.readText() } catch (e: Exception) { return emptyList() }
            if (text.length > 5_000_000) { // too large, corrupted
                try { file.delete() } catch (e: Exception) {}
                return emptyList()
            }
            val obj = try { JSONObject(text) } catch (e: Exception) {
                try { file.delete() } catch (ex: Exception) {}
                return emptyList()
            }
            val genreKey = if (genre == "Авто (из текста)") "general" else genre
            val arr = obj.optJSONArray(genreKey) ?: obj.optJSONArray("general") ?: obj.optJSONArray("all") ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until minOf(arr.length(), 5000)) {
                try { list.add(arr.getString(i)) } catch (e: Exception) { continue }
            }
            list.shuffled().take(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getRelevantTemplates(context: Context, keywords: List<String>, genre: String = "general", limit: Int = 100): List<String> {
        return try {
            val all = getLearnedTemplates(context, genre, 500) + getLearnedTemplates(context, "all", 500)
            if (all.isEmpty() || keywords.isEmpty()) return getLearnedTemplates(context, genre, limit)

            val scored = all.map { template ->
                val lower = template.lowercase()
                var score = 0.0
                for (kw in keywords) {
                    if (lower.contains(kw.lowercase())) {
                        score += 2.0
                        if (Regex("\\b${Regex.escape(kw.lowercase())}\\b").containsMatchIn(lower)) score += 1.0
                    }
                }
                val genreKeywords = when (genre) {
                    "Боевик" -> listOf("выстрел", "цель", "оружие", "память")
                    "Детектив" -> listOf("улика", "дело", "след", "полиция")
                    "Роман" -> listOf("любовь", "сердце", "встреча")
                    "Фантастика" -> listOf("космос", "корабль", "звезда")
                    else -> emptyList()
                }
                for (gk in genreKeywords) if (lower.contains(gk)) score += 0.5
                val len = template.length
                if (len in 50..200) score += 0.5
                score to template
            }.sortedByDescending { it.first }

            scored.filter { it.first > 0 }.take(limit).map { it.second }.ifEmpty {
                getLearnedTemplates(context, genre, limit)
            }
        } catch (e: Exception) {
            getLearnedTemplates(context, genre, limit)
        }
    }

    fun getAllLearnedTemplatesCount(context: Context): Int {
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

    fun getLearnedPlotsByGenre(context: Context, genre: String, limit: Int = 50): List<String> {
        return try {
            val file = getGenrePlotsFile(context, genre)
            if (!file.exists()) return getLearnedPlots(context, limit)
            val obj = try { JSONObject(file.readText()) } catch (e: Exception) { return getLearnedPlots(context, limit) }
            val arr = obj.optJSONArray("plots") ?: return getLearnedPlots(context, limit)
            (0 until arr.length()).mapNotNull { try { arr.getString(it) } catch (e: Exception) { null } }.shuffled().take(limit)
        } catch (e: Exception) { getLearnedPlots(context, limit) }
    }

    private fun saveDetailedPlot(context: Context, book: LearnedBook) {
        try {
            val file = getPlotsDetailedFile(context)
            val arr = if (file.exists()) {
                try { JSONArray(file.readText()) } catch (e: Exception) { JSONArray() }
            } else JSONArray()
            val obj = JSONObject()
            obj.put("title", book.title)
            obj.put("authors", book.authors)
            obj.put("genre", book.genre)
            obj.put("keywords", JSONArray(book.keywords))
            obj.put("chars", book.chars)
            obj.put("date", book.date)
            obj.put("path", book.path)
            arr.put(obj)
            while (arr.length() > 200) arr.remove(0)
            file.writeText(arr.toString())
        } catch (e: Exception) {}
    }

    fun getDetailedPlots(context: Context, limit: Int = 100): List<MathPlotModel.Plot> {
        return try {
            val file = getPlotsDetailedFile(context)
            if (!file.exists()) {
                // Fallback to books index
                return getLearnedBooks(context).take(limit).map { MathPlotModel.learnedBookToPlot(it) }
            }
            val arr = try { JSONArray(file.readText()) } catch (e: Exception) { return emptyList() }
            val list = mutableListOf<MathPlotModel.Plot>()
            for (i in 0 until minOf(arr.length(), limit * 2)) {
                try {
                    val obj = arr.getJSONObject(i)
                    val kwArr = obj.optJSONArray("keywords")
                    val kws = if (kwArr != null) (0 until kwArr.length()).mapNotNull { try { kwArr.getString(it) } catch (e: Exception) { null } } else emptyList()
                    list.add(
                        MathPlotModel.Plot(
                            id = obj.optString("path", obj.optString("title")),
                            keywords = kws,
                            genre = obj.optString("genre", "general"),
                            title = obj.optString("title"),
                            authors = obj.optString("authors"),
                            chars = obj.optInt("chars", 0)
                        )
                    )
                } catch (e: Exception) { continue }
            }
            list
        } catch (e: Exception) {
            getLearnedBooks(context).map { MathPlotModel.learnedBookToPlot(it) }
        }
    }

    fun getDetailedPlotsByGenre(context: Context, genre: String, limit: Int = 50): List<MathPlotModel.Plot> {
        return try {
            val all = getDetailedPlots(context, 200)
            if (genre.startsWith("Авто")) return all.shuffled().take(limit)
            val filtered = all.filter { it.genre.equals(genre, ignoreCase = true) }
            if (filtered.size >= 3) filtered.shuffled().take(limit) else all.shuffled().take(limit)
        } catch (e: Exception) { emptyList() }
    }

    fun getLearnedBooksByGenre(context: Context, genre: String): List<LearnedBook> {
        return try {
            val all = getLearnedBooks(context)
            if (genre.startsWith("Авто")) return all
            all.filter { it.genre.equals(genre, ignoreCase = true) }
        } catch (e: Exception) { emptyList() }
    }

    fun autoLearnByGenre(context: Context, genre: String, maxBooks: Int = 3, progressCallback: ((String) -> Unit)? = null): Int {
        var learnedCount = 0
        try {
            val queries = MathPlotModel.GenreFlibustaMapper.getQueriesForGenre(genre).shuffled().take(maxBooks)
            for (q in queries) {
                try {
                    progressCallback?.invoke("[$genre] Поиск: $q")
                    val opdsBooks = OpdsFetcher.searchFlibusta(q, OpdsFetcher.PRIMARY_OPDS, 0)
                    if (opdsBooks.isEmpty()) {
                        // Try secondary
                        val secondary = OpdsFetcher.searchFlibusta(q, OpdsFetcher.SECONDARY_OPDS, 0)
                        if (secondary.isEmpty()) continue
                        val book = secondary.firstOrNull { it.downloadLinks.isNotEmpty() } ?: continue
                        progressCallback?.invoke("Загрузка: ${book.title.take(20)}")
                        val text = OpdsFetcher.fetchBookText(book) ?: continue
                        if (text.length < 500) continue
                        val learned = learnFromOpdsBook(context, book, text)
                        if (learned != null) learnedCount++
                        Thread.sleep(1500)
                        continue
                    }
                    val book = opdsBooks.firstOrNull { it.downloadLinks.isNotEmpty() } ?: continue
                    progressCallback?.invoke("Загрузка: ${book.title.take(20)}")
                    val text = OpdsFetcher.fetchBookText(book) ?: continue
                    if (text.length < 500) continue
                    progressCallback?.invoke("Обучение: ${book.title.take(20)}")
                    val learned = learnFromOpdsBook(context, book, text)
                    if (learned != null) learnedCount++
                    Thread.sleep(1500)
                } catch (e: Exception) { continue }
            }
        } catch (e: Exception) {}
        return learnedCount
    }

    fun getGenreStats(context: Context): Map<String, Int> {
        return try {
            val books = getLearnedBooks(context)
            books.groupBy { it.genre }.mapValues { it.value.size }
        } catch (e: Exception) { emptyMap() }
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
    }

    private fun saveBookIndex(context: Context, book: LearnedBook) {
        try {
            val dir = getLearnedDir(context)
            val indexFile = File(dir, "books_index.json")
            val arr = if (indexFile.exists()) {
                try { JSONArray(indexFile.readText()) } catch (e: Exception) { JSONArray() }
            } else JSONArray()

            val obj = JSONObject()
            obj.put("title", book.title)
            obj.put("authors", book.authors)
            obj.put("date", book.date)
            obj.put("chars", book.chars)
            obj.put("genre", book.genre)
            obj.put("keywords", JSONArray(book.keywords))
            obj.put("path", book.path)

            arr.put(obj)
            while (arr.length() > 100) arr.remove(0)
            indexFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLearnedBooks(context: Context): List<LearnedBook> {
        return try {
            val indexFile = File(getLearnedDir(context), "books_index.json")
            if (!indexFile.exists()) return emptyList()
            val text = try { indexFile.readText() } catch (e: Exception) { return emptyList() }
            if (text.length > 5_000_000) {
                try { indexFile.delete() } catch (e: Exception) {}
                return emptyList()
            }
            val arr = try { JSONArray(text) } catch (e: Exception) {
                try { indexFile.delete() } catch (ex: Exception) {}
                return emptyList()
            }
            val list = mutableListOf<LearnedBook>()
            for (i in 0 until minOf(arr.length(), 200)) {
                try {
                    val obj = arr.getJSONObject(i)
                    val kwArr = obj.optJSONArray("keywords")
                    val kws = if (kwArr != null) (0 until minOf(kwArr.length(), 20)).mapNotNull { try { kwArr.getString(it) } catch (e: Exception) { null } } else emptyList()
                    list.add(
                        LearnedBook(
                            title = obj.optString("title"),
                            authors = obj.optString("authors"),
                            date = obj.optLong("date"),
                            chars = obj.optInt("chars"),
                            genre = obj.optString("genre"),
                            keywords = kws,
                            path = obj.optString("path")
                        )
                    )
                } catch (e: Exception) { continue }
            }
            list.sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateStats(context: Context, chars: Int, templates: Int) {
        try {
            val file = getStatsFile(context)
            val obj = if (file.exists()) {
                try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
            } else JSONObject()
            val totalChars = obj.optInt("totalChars", 0) + chars
            val totalBooks = obj.optInt("totalBooks", 0) + 1
            val totalTemplates = obj.optInt("totalTemplates", 0) + templates
            val lastLearn = System.currentTimeMillis()
            obj.put("totalChars", totalChars)
            obj.put("totalBooks", totalBooks)
            obj.put("totalTemplates", totalTemplates)
            obj.put("lastLearn", lastLearn)
            file.writeText(obj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getStats(context: Context): JSONObject {
        return try {
            val file = getStatsFile(context)
            if (!file.exists()) JSONObject().apply {
                put("totalChars", 0)
                put("totalBooks", 0)
                put("totalTemplates", 0)
                put("lastLearn", 0)
            } else {
                val text = try { file.readText() } catch (e: Exception) { return JSONObject().apply { put("totalChars", 0); put("totalBooks", 0); put("totalTemplates", 0); put("lastLearn", 0) } }
                try { JSONObject(text) } catch (e: Exception) {
                    try { file.delete() } catch (ex: Exception) {}
                    JSONObject().apply { put("totalChars", 0); put("totalBooks", 0); put("totalTemplates", 0); put("lastLearn", 0) }
                }
            }
        } catch (e: Exception) {
            JSONObject().apply { put("totalChars", 0); put("totalBooks", 0); put("totalTemplates", 0); put("lastLearn", 0) }
        }
    }

    fun clearLearned(context: Context) {
        try {
            getLearnedDir(context).deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun learnFromCurrentText(context: Context, text: String, genre: String = "Авто"): LearnedBook? {
        if (text.length < 100) return null
        return learnFromText(context, "Пользовательский текст ${SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())}", "Пользователь", text, genre)
    }

    fun learnFromFb2File(context: Context, file: File): LearnedBook? {
        return try {
            val fb2 = Fb2Importer.parseFb2File(file) ?: return null
            learnFromText(context, fb2.title, fb2.authors, fb2.content, fb2.genre)
        } catch (e: Exception) {
            null
        }
    }

    fun learnFromEpubFile(context: Context, file: File): LearnedBook? {
        return try {
            val epub = EpubImporter.parseEpub(file) ?: return null
            val genre = StoryGenerator.detectGenreStatic(epub.content)
            learnFromText(context, epub.title, epub.authors, epub.content, genre)
        } catch (e: Exception) {
            null
        }
    }

    fun getTopPopularBooksQuery(): List<String> {
        return listOf(
            "Достоевский", "Толстой", "Пушкин", "Чехов", "Гоголь", "Булгаков", "Набоков",
            "Sherlock Holmes", "Dostoevsky", "Tolstoy",
            "фантастика", "детектив", "любовь", "приключения", "философия",
            "Лукьяненко", "Пелевин", "Стругацкий", "Акунин"
        )
    }

    fun getFlibustaPopularQueries(): List<String> {
        return listOf(
            "Достоевский", "Толстой Лев", "Пушкин", "Гоголь", "Булгаков Мастер",
            "Стругацкий", "Лукьяненко", "Пелевин", "Акунин", "Глуховский",
            "фантастика", "детектив", "любовный роман", "ужасы", "приключения", "философия"
        )
    }

    fun learnFromOpdsBook(context: Context, opdsBook: OpdsFetcher.OpdsBook, content: String): LearnedBook? {
        if (content.length < 200) return null
        val genre = StoryGenerator.detectGenreStatic(content)
        return learnFromText(context, opdsBook.title, opdsBook.authors, content, genre)
    }

    fun autoLearnFromFlibusta(context: Context, maxBooks: Int = 3, progressCallback: ((String) -> Unit)? = null): Int {
        var learnedCount = 0
        try {
            val queries = getFlibustaPopularQueries().shuffled().take(maxBooks)
            for (q in queries) {
                try {
                    progressCallback?.invoke("Поиск Флибуста: $q")
                    val opdsBooks = OpdsFetcher.searchFlibusta(q, OpdsFetcher.PRIMARY_OPDS, 0)
                    if (opdsBooks.isEmpty()) continue
                    val book = opdsBooks.firstOrNull { it.downloadLinks.isNotEmpty() } ?: continue
                    progressCallback?.invoke("Загрузка: ${book.title.take(20)}")
                    val text = OpdsFetcher.fetchBookText(book) ?: continue
                    if (text.length < 500) continue
                    progressCallback?.invoke("Обучение: ${book.title.take(20)}")
                    val learned = learnFromOpdsBook(context, book, text)
                    if (learned != null) learnedCount++
                    Thread.sleep(1500)
                } catch (e: Exception) { continue }
            }
        } catch (e: Exception) {}
        return learnedCount
    }

    fun exportLearnedToZip(context: Context, zipFile: File): Boolean {
        return try {
            val dir = getLearnedDir(context)
            if (!dir.exists()) return false
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = file.relativeTo(dir).path
                    zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importLearnedFromZip(context: Context, zipFile: File): Boolean {
        return try {
            val dir = getLearnedDir(context)
            dir.mkdirs()
            val basePath = dir.canonicalPath + File.separator
            java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(dir, entry.name)
                        // Защита от Zip Slip: имя записи в архиве может содержать "../",
                        // и тогда файл записался бы вне папки обучения.
                        val outPath = try { outFile.canonicalPath } catch (e: Exception) { entry.name }
                        if (!outPath.startsWith(basePath)) {
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }
                        outFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFavoritesFile(context: Context): File {
        return File(getLearnedDir(context), "favorites.json")
    }

    fun getFavorites(context: Context): Set<String> {
        return try {
            val f = getFavoritesFile(context)
            if (!f.exists()) return emptySet()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    fun toggleFavorite(context: Context, path: String): Boolean {
        return try {
            val favs = getFavorites(context).toMutableSet()
            val added = if (favs.contains(path)) {
                favs.remove(path); false
            } else {
                favs.add(path); true
            }
            val arr = JSONArray()
            favs.forEach { arr.put(it) }
            getFavoritesFile(context).writeText(arr.toString())
            added
        } catch (e: Exception) { false }
    }

    fun isFavorite(context: Context, path: String): Boolean {
        return getFavorites(context).contains(path)
    }
}