package com.zakharevich.ziwriter

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object InternetBookFetcher {

    data class BookInfo(
        val id: String,
        val title: String,
        val authors: String,
        val subjects: List<String>,
        val downloadUrl: String?,
        val source: String,
        val languages: List<String> = emptyList()
    )

    data class PlotIdea(
        val title: String,
        val plot: String,
        val source: String
    )

    // Search Gutendex (Project Gutenberg) - free, no key, supports ru
    fun searchGutendex(query: String, language: String = ""): List<BookInfo> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            var urlStr = "https://gutendex.com/books?search=$encoded&page=1"
            if (language.isNotBlank()) urlStr += "&languages=$language"

            val json = httpGet(urlStr) ?: return emptyList()
            val obj = JSONObject(json)
            val results = obj.optJSONArray("results") ?: return emptyList()

            val books = mutableListOf<BookInfo>()
            for (i in 0 until minOf(results.length(), 15)) {
                val book = results.getJSONObject(i)
                val id = book.optInt("id", 0).toString()
                val title = book.optString("title", "Без названия")
                val authorsArr = book.optJSONArray("authors")
                val authors = if (authorsArr != null) {
                    (0 until authorsArr.length()).map { authorsArr.getJSONObject(it).optString("name", "") }.joinToString(", ")
                } else "Неизвестен"
                val subjectsArr = book.optJSONArray("subjects")
                val subjects = if (subjectsArr != null) {
                    (0 until minOf(subjectsArr.length(), 5)).map { subjectsArr.getString(it) }
                } else emptyList()
                val langsArr = book.optJSONArray("languages")
                val langs = if (langsArr != null) (0 until langsArr.length()).map { langsArr.getString(it) } else emptyList()

                val formats = book.optJSONObject("formats")
                var downloadUrl: String? = null
                if (formats != null) {
                    // Prefer txt
                    val keys = formats.keys()
                    var best: String? = null
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k.contains("text/plain") && !k.contains("zip")) {
                            best = formats.optString(k)
                            if (k == "text/plain; charset=utf-8" || k == "text/plain") break
                        }
                    }
                    if (best == null) {
                        // fallback to text/html or first txt
                        val keys2 = formats.keys()
                        while (keys2.hasNext()) {
                            val k = keys2.next()
                            if (k.contains("text/plain")) { best = formats.optString(k); break }
                        }
                    }
                    downloadUrl = best
                }

                books.add(BookInfo(id, title, authors, subjects, downloadUrl, "Gutendex", langs))
            }
            books
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Search Open Library - also free, huge catalog
    fun searchOpenLibrary(query: String): List<BookInfo> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://openlibrary.org/search.json?q=$encoded&limit=15&fields=key,title,author_name,subject,language,isbn"
            val json = httpGet(urlStr) ?: return emptyList()
            val obj = JSONObject(json)
            val docs = obj.optJSONArray("docs") ?: return emptyList()

            val books = mutableListOf<BookInfo>()
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val key = doc.optString("key", "")
                val title = doc.optString("title", "Без названия")
                val authorsArr = doc.optJSONArray("author_name")
                val authors = if (authorsArr != null) (0 until minOf(authorsArr.length(), 3)).joinToString(", ") { authorsArr.getString(it) } else "Неизвестен"
                val subjectsArr = doc.optJSONArray("subject")
                val subjects = if (subjectsArr != null) (0 until minOf(subjectsArr.length(), 5)).map { subjectsArr.getString(it) } else emptyList()
                val langsArr = doc.optJSONArray("language")
                val langs = if (langsArr != null) (0 until langsArr.length()).map { langsArr.getString(it) } else emptyList()

                books.add(BookInfo(key, title, authors, subjects, null, "OpenLibrary", langs))
            }
            books
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Combined search - now includes Flibusta OPDS 213.5.65.159
    fun searchBooks(query: String): List<BookInfo> {
        val gutendex = try { searchGutendex(query) } catch (e: Exception) { emptyList() }
        val openLib = try { searchOpenLibrary(query) } catch (e: Exception) { emptyList() }
        val flibusta = try { searchFlibustaOpds(query) } catch (e: Exception) { emptyList() }
        return (flibusta + gutendex + openLib).distinctBy { it.title + it.authors }.take(25)
    }

    fun searchFlibustaOpds(query: String): List<BookInfo> {
        return try {
            val opdsBooks = OpdsFetcher.searchFlibusta(query, OpdsFetcher.PRIMARY_OPDS, 0)
            opdsBooks.map { OpdsFetcher.toBookInfo(it) }.take(15)
        } catch (e: Exception) { emptyList() }
    }

    fun searchFlibustaOpdsDetailed(query: String): List<OpdsFetcher.OpdsBook> {
        return try { OpdsFetcher.searchFlibusta(query, OpdsFetcher.PRIMARY_OPDS, 0) } catch (e: Exception) { emptyList() }
    }

    // Fetch book text from Gutendex URL or Flibusta OPDS
    fun fetchBookText(downloadUrl: String): String? {
        return try {
            if (downloadUrl.contains("213.5.65.159") || downloadUrl.contains("flibusta") || downloadUrl.contains("/b/") || downloadUrl.contains("/fb2")) {
                val bytes = OpdsFetcher.httpGetBytes(downloadUrl, timeout = 25000)
                if (bytes != null && bytes.isNotEmpty()) {
                    val fakeBook = OpdsFetcher.OpdsBook(downloadUrl, "Flibusta Book", "", "", mapOf("fb2" to downloadUrl, "acquisition" to downloadUrl))
                    val parsed = OpdsFetcher.fetchBookText(fakeBook, "fb2")
                    if (parsed != null && parsed.length > 200) return parsed.take(200000)
                    val raw = try { String(bytes, Charsets.UTF_8) } catch (e: Exception) { String(bytes, charset("windows-1251")) }
                    if (raw.length > 200) return raw.take(200000)
                }
            }
            httpGet(downloadUrl, timeout = 20000)?.take(200000)
        } catch (e: Exception) { null }
    }

    fun fetchFlibustaBookText(opdsBook: OpdsFetcher.OpdsBook): String? {
        return try { OpdsFetcher.fetchBookText(opdsBook) } catch (e: Exception) { null }
    }

    // Fetch random plot idea from Wikipedia or Quotable
    fun fetchRandomPlot(genre: String = ""): PlotIdea? {
        return try {
            // Try Wikipedia random summary RU
            val wiki = try { fetchWikipediaRandom() } catch (e: Exception) { null }
            if (wiki != null) return wiki

            // Fallback: quotable
            val quote = httpGet("https://api.quotable.io/random?tags=wisdom|life|love") ?: return null
            val obj = JSONObject(quote)
            val content = obj.optString("content", "")
            val author = obj.optString("author", "")
            if (content.isNotEmpty()) {
                PlotIdea("Цитата для сюжета: $author", content, "Quotable")
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun fetchWikipediaRandom(): PlotIdea? {
        return try {
            // RU Wikipedia random
            val json = httpGet("https://ru.wikipedia.org/api/rest_v1/page/random/summary") ?: return null
            val obj = JSONObject(json)
            val title = obj.optString("title", "Случайная статья")
            val extract = obj.optString("extract", "")
            if (extract.isNotEmpty()) PlotIdea(title, extract, "Wikipedia RU") else null
        } catch (e: Exception) {
            null
        }
    }

    // Fetch plot ideas by genre using Wikipedia search
    fun fetchPlotByGenre(genre: String): PlotIdea? {
        return try {
            val query = when (genre) {
                "Боевик" -> "спецоперация"
                "Детектив" -> "расследование"
                "Фантастика" -> "космос"
                "Роман" -> "любовь"
                "Приключения" -> "экспедиция"
                "Хоррор" -> "призрак"
                "Юмор" -> "курьез"
                "Философская проза" -> "смысл жизни"
                else -> genre
            }
            val encoded = URLEncoder.encode(query, "UTF-8")
            val json = httpGet("https://ru.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=&srlimit=5") ?: return null
            val obj = JSONObject(json)
            val search = obj.optJSONObject("query")?.optJSONArray("search") ?: return null
            if (search.length() == 0) return null
            val first = search.getJSONObject(0)
            val title = first.optString("title", "")
            val snippet = first.optString("snippet", "").replace(Regex("<[^>]*>"), "")
            if (title.isNotEmpty()) PlotIdea(title, snippet, "Wikipedia Search") else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun httpGet(urlStr: String, timeout: Int = 12000): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.setRequestProperty("User-Agent", "ZI-Writer/6.0 (Android; +375293371412)")
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // Try to read error stream for debugging
                return null
            }
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                    if (sb.length > 500000) break // safety limit
                }
                sb.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            conn?.disconnect()
        }
    }
}
