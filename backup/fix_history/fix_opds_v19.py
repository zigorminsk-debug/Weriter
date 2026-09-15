import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/OpdsFetcher.kt')
code = r'''
package com.zakharevich.ziwriter

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OpdsFetcher {
    data class OpdsBook(
        val id: String,
        val title: String,
        val authors: String,
        val description: String,
        val downloadLinks: Map<String, String>,
        val coverUrl: String? = null,
        val source: String = "Flibusta OPDS"
    )
    const val PRIMARY_OPDS = "http://213.5.65.159/opds"
    private val MIRRORS = listOf(
        "http://213.5.65.159/opds",
        "https://flibusta.is/opds",
        "http://flibusta.is/opds",
        "https://flibusta.site/opds",
        "http://flibusta.site/opds",
        "https://flibusta.appspot.com/opds",
        "http://flibusta.appspot.com/opds",
        "https://flibusta.io/opds",
        "http://flibusta.io/opds"
    )
    
    fun searchFlibusta(query: String, baseUrl: String = PRIMARY_OPDS, page: Int = 0): List<OpdsBook> {
        if (query.isBlank()) return emptyList()
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val urlsToTry = mutableListOf<String>()
        // Пробуем разные форматы поиска
        urlsToTry.add("$baseUrl/search?searchTerm=$encoded&searchType=books&pageNumber=$page")
        urlsToTry.add("$baseUrl/search?searchTerm=$encoded&searchType=books")
        urlsToTry.add("$baseUrl/search?searchTerm=$encoded")
        urlsToTry.add("$baseUrl/search?searchTerm=$encoded&type=books")
        for (mirror in MIRRORS) {
            if (mirror == baseUrl) continue
            urlsToTry.add("$mirror/search?searchTerm=$encoded&searchType=books&pageNumber=$page")
            urlsToTry.add("$mirror/search?searchTerm=$encoded&searchType=books")
        }
        for (url in urlsToTry) {
            try {
                val xml = httpGet(url, timeout = 12000) ?: continue
                if (xml.length < 50) continue
                // Проверяем что это не HTML страница ошибки
                if (xml.trim().startsWith("<!DOCTYPE html") || xml.contains("<html")) continue
                val books = parseOpdsFeed(xml, baseUrl)
                if (books.isNotEmpty()) return books
            } catch (e: Exception) { continue }
        }
        return emptyList()
    }
    
    fun parseOpdsFeed(xmlContent: String, baseUrl: String): List<OpdsBook> {
        val books = mutableListOf<OpdsBook>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlContent.reader())
            var eventType = parser.eventType
            var currentTitle = ""
            var currentId = ""
            var currentAuthor = ""
            var currentDescription = ""
            var currentLinks = mutableMapOf<String, String>()
            var currentCover: String? = null
            var inEntry = false
            var inAuthor = false
            var currentTextTag = ""
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        currentTextTag = name
                        if (name == "entry") {
                            inEntry = true
                            currentTitle = ""
                            currentId = ""
                            currentAuthor = ""
                            currentDescription = ""
                            currentLinks = mutableMapOf()
                            currentCover = null
                        } else if (inEntry) {
                            if (name == "author") inAuthor = true
                            else if (name == "link") {
                                val rel = parser.getAttributeValue(null, "rel") ?: ""
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                val href = parser.getAttributeValue(null, "href") ?: ""
                                if (href.isNotEmpty()) {
                                    val fullHref = resolveUrl(baseUrl, href)
                                    when {
                                        rel.contains("acquisition") || rel.contains("open-access") || type.contains("fb2") || type.contains("epub") || type.contains("text") -> {
                                            val key = when {
                                                type.contains("fb2") || href.contains("fb2") -> "fb2"
                                                type.contains("epub") || href.contains("epub") -> "epub"
                                                type.contains("text/plain") || type.contains("txt") || href.contains("txt") -> "txt"
                                                else -> "fb2"
                                            }
                                            if (!currentLinks.containsKey(key)) currentLinks[key] = fullHref
                                            if (!currentLinks.containsKey("acquisition")) currentLinks["acquisition"] = fullHref
                                        }
                                        rel.contains("image") || type.startsWith("image/") -> {
                                            if (currentCover == null) currentCover = fullHref
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inEntry) {
                            val text = parser.text ?: ""
                            when (currentTextTag) {
                                "title" -> { if (!inAuthor) currentTitle += text else currentAuthor += text }
                                "id" -> currentId += text
                                "content", "summary" -> currentDescription += text
                                "name" -> if (inAuthor) currentAuthor += text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "entry" && inEntry) {
                            inEntry = false
                            if (currentTitle.isNotBlank() && currentLinks.isNotEmpty()) {
                                val idClean = currentId.ifBlank { currentTitle }
                                if (!currentTitle.lowercase().contains("поиск по") && currentTitle.length > 2 && !currentTitle.lowercase().contains("search")) {
                                    books.add(OpdsBook(idClean, currentTitle.trim(), currentAuthor.trim().ifEmpty { "Неизвестен" }, currentDescription.trim(), currentLinks.toMap(), currentCover))
                                }
                            }
                        } else if (name == "author") inAuthor = false
                        currentTextTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {}
        return books
    }
    
    fun resolveUrl(baseUrl: String, href: String): String {
        return try {
            if (href.startsWith("http://") || href.startsWith("https://")) href
            else {
                val domain = baseUrl.substringBefore("/opds")
                if (href.startsWith("/")) domain + href else "$domain/$href"
            }
        } catch (e: Exception) { href }
    }
    
    fun fetchBookText(opdsBook: OpdsBook, preferredFormat: String = "fb2"): String? {
        val order = listOf(preferredFormat, "fb2", "txt", "epub", "acquisition")
        for (fmt in order) {
            val url = opdsBook.downloadLinks[fmt] ?: continue
            try {
                val bytes = httpGetBytes(url, timeout = 25000) ?: continue
                if (bytes.isEmpty() || bytes.size < 100) continue
                val text = when {
                    fmt == "fb2" || url.contains("fb2") -> tryParseFb2Bytes(bytes)
                    fmt == "epub" || url.contains("epub") -> tryParseEpubBytes(bytes)
                    else -> try { String(bytes, Charsets.UTF_8).takeIf { it.length > 100 } ?: String(bytes, charset("windows-1251")) } catch (e: Exception) { null }
                }
                if (text != null && text.length > 200) return text.take(300000)
            } catch (e: Exception) { continue }
        }
        return null
    }
    
    private fun tryParseFb2Bytes(bytes: ByteArray): String? {
        return try {
            if (bytes.size > 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                val zis = java.util.zip.ZipInputStream(bytes.inputStream())
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().contains("fb2")) {
                        val fb2Text = zis.readBytes().toString(Charsets.UTF_8)
                        val parsed = parseFb2XmlToText(fb2Text)
                        if (parsed.length > 200) { zis.close(); return parsed }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()
                null
            } else {
                parseFb2XmlToText(String(bytes, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            try { parseFb2XmlToText(String(bytes, charset("windows-1251"))) } catch (e2: Exception) { null }
        }
    }
    
    private fun parseFb2XmlToText(fb2Xml: String): String {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(fb2Xml.reader())
            val sb = StringBuilder()
            var eventType = parser.eventType
            var inP = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "p" || name == "v" || name == "subtitle") inP = true
                    }
                    XmlPullParser.TEXT -> {
                        if (inP) {
                            val t = parser.text?.trim() ?: ""
                            if (t.length > 2) { sb.append(t).append("\n\n"); if (sb.length > 250000) break }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "p" || name == "v") inP = false
                    }
                }
                eventType = parser.next()
            }
            sb.toString().ifBlank { fb2Xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(200000) }
        } catch (e: Exception) {
            fb2Xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(200000)
        }
    }
    
    private fun tryParseEpubBytes(bytes: ByteArray): String? {
        return try {
            val zis = java.util.zip.ZipInputStream(bytes.inputStream())
            var entry = zis.nextEntry
            val sb = StringBuilder()
            while (entry != null) {
                if (!entry.isDirectory && (entry.name.endsWith(".html") || entry.name.endsWith(".xhtml") || entry.name.endsWith(".htm"))) {
                    val html = zis.readBytes().toString(Charsets.UTF_8)
                    val text = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                    if (text.length > 100) { sb.append(text).append("\n\n"); if (sb.length > 200000) break }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            if (sb.length > 200) sb.toString() else null
        } catch (e: Exception) { null }
    }
    
    fun httpGet(urlStr: String, timeout: Int = 12000): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "ZI-Writer/19.0 CLEAN")
            conn.setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml, */*")
            conn.setRequestProperty("Accept-Language", "ru,en;q=0.9")
            val code = conn.responseCode
            if (code != 200) {
                // Try to read error stream for debugging
                return null
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) { sb.append(line).append("\n"); if (sb.length > 800000) break }
            sb.toString()
        } catch (e: Exception) { null } finally { conn?.disconnect() }
    }
    
    fun httpGetBytes(urlStr: String, timeout: Int = 20000): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "ZI-Writer/19.0 CLEAN")
            val code = conn.responseCode
            if (code != 200) return null
            conn.inputStream.readBytes()
        } catch (e: Exception) { null } finally { conn?.disconnect() }
    }
    
    fun toBookInfo(opdsBook: OpdsBook): InternetBookFetcher.BookInfo {
        val bestUrl = opdsBook.downloadLinks["fb2"] ?: opdsBook.downloadLinks["txt"] ?: opdsBook.downloadLinks["epub"] ?: opdsBook.downloadLinks["acquisition"]
        return InternetBookFetcher.BookInfo(opdsBook.id, opdsBook.title, opdsBook.authors, if (opdsBook.description.isNotBlank()) listOf(opdsBook.description.take(100)) else emptyList(), bestUrl, opdsBook.source, listOf("ru"))
    }
}
'''
p.write_text(code, encoding='utf-8')
print("opds v19 written")
