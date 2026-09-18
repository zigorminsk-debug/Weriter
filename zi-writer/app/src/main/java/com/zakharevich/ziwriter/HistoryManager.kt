package com.zakharevich.ziwriter

import android.content.Context
import java.io.File

object HistoryManager {

    data class HistoryItem(
        val title: String,
        val fileName: String,
        val chars: Int,
        val date: Long,
        val path: String
    )

    /** Сколько знаков «шапки» читаем, чтобы не тянуть в память весь сохранённый текст. */
    private const val HEAD_LIMIT = 600

    fun saveToHistory(context: Context, story: StoryGenerator.GeneratedStory): File? {
        return try {
            val dir = File(context.filesDir, "history")
            if (!dir.exists()) dir.mkdirs()
            val safeTitle = story.title.replace(Regex("[^A-Za-zА-Яа-я0-9]"), "_").take(30)
            val fileName = "${System.currentTimeMillis()}_${safeTitle}.txt"
            val file = File(dir, fileName)
            // Название в шапке должно быть в одну строку, иначе заголовок не разберётся
            // обратно в getHistory().
            val titleLine = story.title.replace(Regex("\\s+"), " ").trim()
            file.writeText("Название: $titleLine\nЛистов: ${story.pages}\nЗнаков: ${story.chars}\nГерои: ${story.heroes.joinToString(", ")}\nКлючевые: ${story.keywords.joinToString(", ")}\nДата: ${java.util.Date()}\n\n${story.fullText}")
            file
        } catch (e: Exception) {
            null
        }
    }

    /** Читает только начало файла — полный текст в память не поднимаем. */
    private fun readHead(file: File, limit: Int): String {
        return try {
            file.bufferedReader().use { reader ->
                val buffer = CharArray(limit)
                var total = 0
                while (total < limit) {
                    val read = reader.read(buffer, total, limit - total)
                    if (read <= 0) break
                    total += read
                }
                String(buffer, 0, total)
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun getHistory(context: Context): List<HistoryItem> {
        return try {
            val dir = File(context.filesDir, "history")
            if (!dir.exists()) return emptyList()
            dir.listFiles()?.mapNotNull { file ->
                try {
                    // Раньше здесь читался ВЕСЬ файл (file.readText()) и только потом брались
                    // первые 500 знаков: на библиотеке из десятков книг это лишние мегабайты
                    // чтения и тормоза.
                    val head = readHead(file, HEAD_LIMIT)
                    val titleMatch = Regex("Название: (.+)").find(head)
                    val title = titleMatch?.groupValues?.get(1)?.trim().orEmpty().ifEmpty { file.nameWithoutExtension }
                    // «Знаков» берём из шапки (это знаки текста книги), а не размер файла в байтах.
                    val chars = Regex("Знаков: (\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull()
                        ?: file.length().toInt()
                    HistoryItem(
                        title = title,
                        fileName = file.name,
                        chars = chars,
                        date = file.lastModified(),
                        path = file.absolutePath
                    )
                } catch (e: Exception) {
                    null
                }
            }?.sortedByDescending { it.date } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadHistoryItem(path: String): String? {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            null
        }
    }

    fun clearHistory(context: Context) {
        try {
            val dir = File(context.filesDir, "history")
            dir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {}
    }
}
