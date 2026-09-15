package com.zakharevich.ziwriter

import java.io.File
import java.util.zip.ZipFile

object EpubImporter {

    data class EpubBook(
        val title: String,
        val authors: String,
        val content: String,
        val chapters: List<String>
    )

    fun parseEpub(file: File): EpubBook? {
        return try {
            ZipFile(file).use { zip ->
                var opfPath = "OEBPS/content.opf"
                try {
                    val containerEntry = zip.getEntry("META-INF/container.xml")
                    if (containerEntry != null) {
                        val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                        val match = Regex("""full-path="([^"]+)"""").find(containerXml)
                        if (match != null) opfPath = match.groupValues[1]
                    }
                } catch (e: Exception) {}

                var title = file.nameWithoutExtension
                var authors = "Неизвестен"

                try {
                    val opfEntry = zip.getEntry(opfPath)
                    if (opfEntry != null) {
                        val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
                        val titleMatch = Regex("""<dc:title[^>]*>([^<]+)</dc:title>""").find(opfXml)
                        if (titleMatch != null) title = titleMatch.groupValues[1].trim()
                        val creatorMatch = Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""").find(opfXml)
                        if (creatorMatch != null) authors = creatorMatch.groupValues[1].trim()
                    }
                } catch (e: Exception) {}

                val contentBuilder = StringBuilder()
                val chapters = mutableListOf<String>()
                val entries = zip.entries().toList().filter {
                    it.name.endsWith(".xhtml") || it.name.endsWith(".html") || it.name.endsWith(".htm")
                }.sortedBy { it.name }

                for (entry in entries.take(50)) {
                    try {
                        val html = zip.getInputStream(entry).bufferedReader().readText()
                        val text = htmlToText(html)
                        if (text.length > 100) {
                            chapters.add(text.take(5000))
                            contentBuilder.append(text).append("\n\n")
                            if (contentBuilder.length > 500000) break
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }

                val content = contentBuilder.toString().trim()
                if (content.length < 200) return null

                EpubBook(title, authors, content, chapters)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun htmlToText(html: String): String {
        var text = html
        text = text.replace(Regex("""<script[^>]*>.*?</script>""", RegexOption.DOT_MATCHES_ALL), " ")
        text = text.replace(Regex("""<style[^>]*>.*?</style>""", RegexOption.DOT_MATCHES_ALL), " ")
        text = text.replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("""</h[1-6]>""", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("""<br[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("""<[^>]+>"""), " ")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
        text = text.replace(Regex("""[ \t]+"""), " ")
        text = text.replace(Regex("""\n +"""), "\n")
        text = text.replace(Regex("""\n{3,}"""), "\n\n")
        return text.trim()
    }
}
