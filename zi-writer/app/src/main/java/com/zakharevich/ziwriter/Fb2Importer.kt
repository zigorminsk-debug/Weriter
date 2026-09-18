package com.zakharevich.ziwriter

import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

object Fb2Importer {

    data class Fb2Book(
        val title: String,
        val authors: String,
        val genre: String,
        val annotation: String,
        val content: String
    )

    fun parseFb2(input: InputStream): Fb2Book? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            // Безопасный разбор недоверенного XML (FB2 приходит из интернета или из файла
            // пользователя): запрещаем загрузку внешних DTD и сущностей, чтобы исключить XXE.
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (e: Exception) {}
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (e: Exception) {}
            try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (e: Exception) {}
            try { factory.setXIncludeAware(false) } catch (e: Exception) {}
            try { factory.isExpandEntityReferences = false } catch (e: Exception) {}
            // Ограничиваем разворачивание сущностей («billion laughs»). DOCTYPE в FB2
            // запрещать нельзя — он встречается в старых файлах, поэтому ставим только лимит.
            try { factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit", "10000") } catch (e: Exception) {}
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(input)
            doc.documentElement.normalize()

            var title = "Без названия"
            val titleInfo = doc.getElementsByTagName("title-info")
            if (titleInfo.length > 0) {
                val ti = titleInfo.item(0)
                val children = ti.childNodes
                for (i in 0 until children.length) {
                    val node = children.item(i)
                    if (node.nodeName == "book-title") {
                        title = node.textContent.trim()
                        break
                    }
                }
            }

            var authors = "Неизвестен"
            try {
                val authorsList = mutableListOf<String>()
                val authorNodes = doc.getElementsByTagName("author")
                for (i in 0 until authorNodes.length) {
                    val authorNode = authorNodes.item(i)
                    var first = ""
                    var last = ""
                    val kids = authorNode.childNodes
                    for (j in 0 until kids.length) {
                        val k = kids.item(j)
                        when (k.nodeName) {
                            "first-name" -> first = k.textContent.trim()
                            "last-name" -> last = k.textContent.trim()
                            "nickname" -> if (first.isEmpty() && last.isEmpty()) first = k.textContent.trim()
                        }
                    }
                    val full = "$first $last".trim()
                    if (full.isNotEmpty()) authorsList.add(full)
                }
                if (authorsList.isNotEmpty()) authors = authorsList.joinToString(", ")
            } catch (e: Exception) {}

            var genre = "Авто"
            try {
                val genreNodes = doc.getElementsByTagName("genre")
                if (genreNodes.length > 0) {
                    val g = genreNodes.item(0).textContent.trim().lowercase()
                    genre = mapFb2Genre(g)
                }
            } catch (e: Exception) {}

            var annotation = ""
            try {
                val annNodes = doc.getElementsByTagName("annotation")
                if (annNodes.length > 0) {
                    annotation = annNodes.item(0).textContent.trim().take(1000)
                }
            } catch (e: Exception) {}

            val contentBuilder = StringBuilder()
            try {
                val bodies = doc.getElementsByTagName("body")
                for (b in 0 until bodies.length) {
                    val body = bodies.item(b)
                    val attrs = body.attributes
                    if (attrs != null) {
                        val nameAttr = attrs.getNamedItem("name")
                        if (nameAttr != null && nameAttr.nodeValue == "notes") continue
                    }
                    val pNodes = (body as org.w3c.dom.Element).getElementsByTagName("p")
                    for (i in 0 until pNodes.length) {
                        val p = pNodes.item(i).textContent.trim()
                        if (p.length > 10) {
                            contentBuilder.append(p).append("\n\n")
                            if (contentBuilder.length > 500000) break
                        }
                    }
                    if (contentBuilder.length > 500000) break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val content = contentBuilder.toString().trim()
            if (content.length < 100) return null

            Fb2Book(title, authors, genre, annotation, content)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseFb2File(file: File): Fb2Book? {
        return try {
            file.inputStream().use { parseFb2(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun mapFb2Genre(fb2Genre: String): String {
        return when {
            fb2Genre.contains("sf") || fb2Genre.contains("fantasy") || fb2Genre.contains("sci") -> "Фантастика"
            fb2Genre.contains("detective") || fb2Genre.contains("thriller") -> "Детектив"
            fb2Genre.contains("action") || fb2Genre.contains("war") -> "Боевик"
            fb2Genre.contains("love") || fb2Genre.contains("romance") -> "Роман"
            fb2Genre.contains("adventure") -> "Приключения"
            fb2Genre.contains("horror") -> "Хоррор"
            fb2Genre.contains("humor") || fb2Genre.contains("comedy") -> "Юмор"
            fb2Genre.contains("philosophy") || fb2Genre.contains("psychology") -> "Философская проза"
            else -> "Драма"
        }
    }
}
