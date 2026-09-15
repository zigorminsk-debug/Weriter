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

    fun saveToHistory(context: Context, story: StoryGenerator.GeneratedStory): File? {
        return try {
            val dir = File(context.filesDir, "history")
            if (!dir.exists()) dir.mkdirs()
            val safeTitle = story.title.replace(Regex("[^A-Za-zА-Яа-я0-9]"), "_").take(30)
            val fileName = "${System.currentTimeMillis()}_${safeTitle}.txt"
            val file = File(dir, fileName)
            file.writeText("Название: ${story.title}\nЛистов: ${story.pages}\nЗнаков: ${story.chars}\nГерои: ${story.heroes.joinToString(", ")}\nКлючевые: ${story.keywords.joinToString(", ")}\nДата: ${java.util.Date()}\n\n${story.fullText}")
            file
        } catch (e: Exception) {
            null
        }
    }

    fun getHistory(context: Context): List<HistoryItem> {
        return try {
            val dir = File(context.filesDir, "history")
            if (!dir.exists()) return emptyList()
            dir.listFiles()?.mapNotNull { file ->
                try {
                    val content = file.readText().take(500)
                    val titleMatch = Regex("Название: (.+)").find(content)
                    val title = titleMatch?.groupValues?.get(1) ?: file.nameWithoutExtension
                    HistoryItem(
                        title = title,
                        fileName = file.name,
                        chars = file.length().toInt(),
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
