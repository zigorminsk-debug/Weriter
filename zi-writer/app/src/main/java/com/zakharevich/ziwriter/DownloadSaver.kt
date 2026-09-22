package com.zakharevich.ziwriter

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Копирование готового файла (PDF/DOCX/EPUB/TXT/PNG) в публичную папку «Загрузки».
 *
 * Раньше экспортёры писали напрямую в "/storage/emulated/0/Download". Начиная с
 * Android 10 (API 29) такой прямой доступ запрещён (scoped storage), поэтому на
 * современных устройствах файл молча оставался в приватной папке приложения.
 * Здесь используется MediaStore (API 29+, не требует разрешений), а на более
 * старых версиях — прямая запись (нужно разрешение WRITE_EXTERNAL_STORAGE).
 *
 * Возвращает файл, доступный пользователю, либо null — тогда вызывающий код
 * остаётся со своей копией в getExternalFilesDir().
 */
object DownloadSaver {

    fun save(context: Context, source: File, mimeType: String): File? {
        if (!source.exists() || source.length() == 0L) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, mimeType)
        } else {
            saveLegacy(source)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, source: File, mimeType: String): File? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return null

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            // MediaStore может сохранить файл под именем с суффиксом «(1)» при конфликте,
            // поэтому возвращаем путь, который реально будет существовать.
            val expectedFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                source.name
            )
            if (expectedFile.exists()) expectedFile else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveLegacy(source: File): File? {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists() && !dir.mkdirs()) return null
            val target = File(dir, source.name)
            source.copyTo(target, overwrite = true)
            if (target.exists()) target else null
        } catch (e: Exception) {
            null
        }
    }
}
