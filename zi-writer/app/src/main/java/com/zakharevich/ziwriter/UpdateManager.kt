package com.zakharevich.ziwriter

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * UpdateManager — проверка и установка обновлений с GitHub Releases
 *
 * Использует GitHub API для получения информации о последней версии:
 *   GET https://api.github.com/repos/{owner}/{repo}/releases/latest
 *
 * Процесс обновления:
 * 1. Проверяем текущую версию приложения
 * 2. Сравниваем с последней версией на GitHub (tag_name)
 * 3. Если есть новая версия — скачиваем APK через DownloadManager
 * 4. После загрузки — запускаем установку через системный инсталлятор
 */

object UpdateManager {

    private const val TAG = "UpdateManager"
    
    // Репозиторий на GitHub (owner/repo)
    private const val GITHUB_OWNER = "zigorminsk-debug"
    private const val GITHUB_REPO = "Weriter"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    
    // Имя пакета для установки (используется для поиска APK в релизе)
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /**
     * Данные о доступной версии на GitHub
     */
    data class ReleaseInfo(
        val tagName: String,          // "32.0" или "v32.0"
        val versionName: String,      // "32.0" (очищенная от префикса)
        val versionCode: Long,        // числовой код версии (извлекается из tagName)
        val releaseNotes: String,     // описание изменений
        val apkUrl: String?,          // URL для скачивания APK
        val apkFileName: String?,     // имя APK файла
        val publishedAt: String,      // дата публикации
        val isAvailable: Boolean      // есть ли обновление
    )

    /**
     * Проверяет наличие новой версии на GitHub
     * 
     * @return ReleaseInfo с информацией о версии или null, если проверка не удалась
     */
    suspend fun checkForUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Проверка обновлений...")
            
            val currentVersionCode = getCurrentVersionCode(context)
            val currentVersionName = getCurrentVersionName(context)
            
            Log.d(TAG, "Текущая версия: $currentVersionName (code: $currentVersionCode)")
            
            // Получаем информацию о последнем релизе с GitHub
            val json = httpGet(RELEASES_API) ?: return@withContext null
            val release = JSONObject(json)
            
            val tagName = release.optString("tag_name", "")
            val releaseNotes = release.optString("body", "")
            val publishedAt = release.optString("published_at", "")
            
            if (tagName.isEmpty()) {
                Log.w(TAG, "tag_name пустой в ответе GitHub API")
                return@withContext null
            }
            
            // Извлекаем номер версии из tag_name (убираем "v" если есть)
            val versionName = tagName.removePrefix("v").removePrefix("V").trim()
            val latestVersionCode = parseVersionCode(versionName)
            
            Log.d(TAG, "Последняя версия на GitHub: $versionName (code: $latestVersionCode)")
            
            // Проверяем, есть ли новая версия
            val isAvailable = latestVersionCode > currentVersionCode
            
            // Ищем APK файл в assets релиза
            val assets = release.optJSONArray("assets")
            var apkUrl: String? = null
            var apkFileName: String? = null
            
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = downloadUrl
                        apkFileName = name
                        Log.d(TAG, "Найден APK: $name -> $downloadUrl")
                        break
                    }
                }
            }
            
            ReleaseInfo(
                tagName = tagName,
                versionName = versionName,
                versionCode = latestVersionCode,
                releaseNotes = releaseNotes,
                apkUrl = apkUrl,
                apkFileName = apkFileName,
                publishedAt = publishedAt,
                isAvailable = isAvailable
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки обновлений: ${e.message}", e)
            null
        }
    }

    /**
     * Скачивает APK через системный DownloadManager и запускает установку
     * 
     * @param context контекст приложения
     * @param apkUrl URL для скачивания APK
     * @param fileName имя файла для сохранения
     * @return true если загрузка запущена успешно
     */
    fun downloadAndInstall(context: Context, apkUrl: String, fileName: String): Boolean {
        return try {
            Log.d(TAG, "Запуск загрузки: $apkUrl")
            
            // Определяем, куда сохранять файл
            val downloadDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: используем MediaStore или приватную папку
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                // Android 9 и ниже: публичная папка Downloads
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            
            downloadDir?.mkdirs()
            val apkFile = File(downloadDir, fileName)
            
            // Удаляем старый файл если есть
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            // Используем системный DownloadManager
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("ZI Writer Update")
                setDescription("Загрузка обновления...")
                setMimeType(APK_MIME_TYPE)
                
                // Показываем уведомление о загрузке
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // Сохраняем в публичную папку Downloads
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                
                // Разрешаем загрузку по мобильной сети и Wi-Fi
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            Log.d(TAG, "Загрузка запущена, ID: $downloadId")
            
            // Регистрируем receiver для отслеживания завершения загрузки
            registerDownloadCompleteReceiver(context, downloadId, apkFile)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска загрузки: ${e.message}", e)
            false
        }
    }

    /**
     * Регистрирует BroadcastReceiver для отслеживания завершения загрузки
     */
    private fun registerDownloadCompleteReceiver(context: Context, downloadId: Long, expectedFile: File) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Log.d(TAG, "Загрузка завершена, ID: $downloadId")
                    
                    // Проверяем статус загрузки
                    val downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Log.d(TAG, "Загрузка успешна")
                                val localUri = cursor.getString(
                                    cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                )
                                val apkFile = if (localUri != null) {
                                    File(Uri.parse(localUri).path ?: expectedFile.absolutePath)
                                } else {
                                    expectedFile
                                }
                                
                                // Запускаем установку APK
                                installApk(ctx, apkFile)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(
                                    cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                )
                                Log.e(TAG, "Загрузка не удалась, reason: $reason")
                            }
                        }
                        cursor.close()
                    }
                    
                    // Отменяем регистрацию receiver
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // Receiver уже unregistered
                    }
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        context.registerReceiver(receiver, filter)
    }

    /**
     * Запускает установку APK через системный инсталлятор
     */
    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK файл не найден: ${apkFile.absolutePath}")
            return
        }
        
        Log.d(TAG, "Установка APK: ${apkFile.absolutePath}")
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // Получаем URI через FileProvider (безопасный способ для Android 7+)
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            setDataAndType(apkUri, APK_MIME_TYPE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            
            // Для Android 8+ нужно разрешение на установку из неизвестных источников
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    // Открываем настройки для разрешения установки
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }
        }
        
        context.startActivity(intent)
    }

    /**
     * Получает код версии текущего приложения
     */
    private fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Не удалось получить версию приложения", e)
            0L
        }
    }

    /**
     * Получает имя версии текущего приложения
     */
    private fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Не удалось получить имя версии", e)
            "unknown"
        }
    }

    /**
     * Парсит номер версии из строки (например, "32.0" -> 3200, "32.1.2" -> 320102)
     * 
     * Формат: major.minor.patch
     * major * 10000 + minor * 100 + patch
     */
    private fun parseVersionCode(versionString: String): Long {
        return try {
            val parts = versionString.split(".")
            when (parts.size) {
                1 -> (parts[0].toLongOrNull() ?: 0) * 10000
                2 -> (parts[0].toLongOrNull() ?: 0) * 10000 + (parts[1].toLongOrNull() ?: 0) * 100
                3 -> (parts[0].toLongOrNull() ?: 0) * 10000 + (parts[1].toLongOrNull() ?: 0) * 100 + (parts[2].toLongOrNull() ?: 0)
                else -> (parts[0].toLongOrNull() ?: 0) * 10000
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка парсинга версии: $versionString", e)
            0L
        }
    }

    /**
     * Выполняет HTTP GET запрос и возвращает тело ответа как строку
     */
    private fun httpGet(urlStr: String, timeout: Int = 15000): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "ZI-Writer-UpdateChecker")
            
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP $responseCode from $urlStr")
                return null
            }
            
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP GET error: ${e.message}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Проверяет, разрешена ли установка из неизвестных источников
     */
    fun canInstallFromUnknownSources(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Для Android 7 и ниже разрешение даётся через Settings
            @Suppress("DEPRECATION")
            android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS,
                0
            ) == 1
        }
    }

    /**
     * Открывает настройки для разрешения установки из неизвестных источников
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
