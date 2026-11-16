package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"

    const val CURRENT_VERSION_CODE = 12
    const val CURRENT_VERSION_NAME = "1.4.8"

    const val TELEGRAM_CHANNEL_URL = "https://t.me/AS3SA"
    const val TELEGRAM_DEEP_LINK = "tg://resolve?domain=AS3SA"
    const val DIRECT_APK_DOWNLOAD_URL = "https://ais-dev-u3bbyddierfnvqnk2dov33-152810655533.europe-west2.run.app/salati-app.apk"
    private const val REMOTE_VERSION_URL = "https://ais-dev-u3bbyddierfnvqnk2dov33-152810655533.europe-west2.run.app/version.json"
    private const val SHARED_VERSION_URL = "https://ais-pre-u3bbyddierfnvqnk2dov33-152810655533.europe-west2.run.app/version.json"
    private const val LOCAL_DEV_VERSION_URL = "http://127.0.0.1:8080/version.json"
    private const val LOCAL_DEV_APK_URL = "http://127.0.0.1:8080/salati-app.apk"

    // No-redirect probe client for checking JSON feeds cleanly so 302 auth walls are immediately detected
    private val jsonProbeClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()

    // Full redirect client for downloading APK binaries and GitHub raw/CDN assets
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun getInstalledVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Exception) {
            CURRENT_VERSION_CODE
        }
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: CURRENT_VERSION_NAME
        } catch (_: Exception) {
            CURRENT_VERSION_NAME
        }
    }

    /**
     * Expands a user-provided custom URL or GitHub repository URL into concrete candidate endpoints.
     * Supports:
     * - Direct JSON URL (e.g. raw.githubusercontent.com/.../version.json, Gist raw, custom host)
     * - GitHub repo URL (https://github.com/owner/repo -> checks raw main/master version.json + GitHub Releases latest)
     */
    internal fun resolveCandidateUpdateUrls(customFeedUrl: String): List<String> {
        val trimmed = customFeedUrl.trim()
        val result = mutableListOf<String>()

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val githubMatch = Regex("^https?://github\\.com/([^/]+)/([^/?#]+?)(?:\\.git)?/?$").find(trimmed)
            if (githubMatch != null) {
                val owner = githubMatch.groupValues[1]
                val repo = githubMatch.groupValues[2]
                result.add("https://raw.githubusercontent.com/$owner/$repo/main/download-web/version.json")
                result.add("https://raw.githubusercontent.com/$owner/$repo/master/download-web/version.json")
                result.add("https://api.github.com/repos/$owner/$repo/releases/latest")
            } else {
                result.add(trimmed)
            }
        }

        result.add(REMOTE_VERSION_URL)
        result.add(SHARED_VERSION_URL)
        result.add(LOCAL_DEV_VERSION_URL)
        return result.distinct()
    }

    /**
     * Compares remote vs installed versions cleanly, supporting both integer versionCode
     * and semantic version names (e.g. "1.5.0" > "1.4.4" for GitHub Releases).
     */
    internal fun isRemoteVersionNewer(
        latestCode: Int,
        installedCode: Int,
        latestName: String,
        installedName: String
    ): Boolean {
        if (latestCode > installedCode) return true
        if (latestCode in 1 until installedCode) return false

        val remoteParts = latestName.trimStart('v', 'V').split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val localParts = installedName.trimStart('v', 'V').split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun checkRemoteUpdate(context: Context): AppUpdateInfo = withContext(Dispatchers.IO) {
        val installedCode = getInstalledVersionCode(context)
        val installedName = getInstalledVersionName(context)
        val customFeedUrl = com.example.data.PreferencesManager(context).customUpdateUrl.trim()
        val urlsToTry = resolveCandidateUpdateUrls(customFeedUrl)

        var sawAiStudioAuthWall = false

        for (url in urlsToTry) {
            try {
                val client = if (url.contains("github") || url == customFeedUrl) httpClient else jsonProbeClient
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SalatiApp-Android/$installedName")
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache, no-store, max-age=0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        val loc = response.header("Location") ?: ""
                        if (loc.contains("__cookie_check") || loc.contains("accounts.google.com")) {
                            sawAiStudioAuthWall = true
                        }
                        return@use
                    }

                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trimStart('\uFEFF')?.trim()
                        if (!body.isNullOrBlank()) {
                            if (body.startsWith("<") && (body.contains("__cookie_check") || body.contains("_aistudio-iframe"))) {
                                sawAiStudioAuthWall = true
                                return@use
                            }
                            if (body.startsWith("{")) {
                                val json = JSONObject(body)
                                val parsed = parseVersionOrGithubJson(json, installedCode, installedName)
                                if (parsed != null) {
                                    return@withContext parsed
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Update check failed on $url: ${e.message}")
            }
        }

        val isOnline = com.example.data.OnlinePrayerSyncManager.isNetworkAvailable(context)
        if (!isOnline) {
            return@withContext AppUpdateInfo(
                isUpdateAvailable = false,
                isCheckFailed = true,
                currentVersionCode = installedCode,
                currentVersionName = installedName,
                latestVersionCode = installedCode,
                latestVersionName = installedName,
                telegramChannelUrl = TELEGRAM_CHANNEL_URL,
                directApkDownloadUrl = DIRECT_APK_DOWNLOAD_URL,
                checkStatusMessage = "لا يوجد اتصال بالإنترنت. يرجى التأكد من اتصالك بالشبكة."
            )
        }

        if (sawAiStudioAuthWall && customFeedUrl.isBlank()) {
            return@withContext AppUpdateInfo(
                isUpdateAvailable = false,
                isCheckFailed = false,
                isDevServerAuthLocked = true,
                currentVersionCode = installedCode,
                currentVersionName = installedName,
                latestVersionCode = installedCode,
                latestVersionName = installedName,
                telegramChannelUrl = TELEGRAM_CHANNEL_URL,
                directApkDownloadUrl = DIRECT_APK_DOWNLOAD_URL,
                checkStatusMessage = "خادم المعاينة المؤقت في AI Studio متوقف حالياً بعد سكون الجلسة. لربط تحديث دائم 24/7 أضف رابط GitHub أو JSON أدناه، أو تابع @AS3SA."
            )
        }

        AppUpdateInfo(
            isUpdateAvailable = false,
            isCheckFailed = true,
            currentVersionCode = installedCode,
            currentVersionName = installedName,
            latestVersionCode = installedCode,
            latestVersionName = installedName,
            telegramChannelUrl = TELEGRAM_CHANNEL_URL,
            directApkDownloadUrl = DIRECT_APK_DOWNLOAD_URL,
            checkStatusMessage = "تعذر الوصول لخادم التحديثات حالياً. تأكد من رابط التحديث أو تابع قناة @AS3SA."
        )
    }

    internal fun parseVersionOrGithubJson(
        json: JSONObject,
        installedCode: Int,
        installedName: String
    ): AppUpdateInfo? {
        // GitHub Releases API format (has tag_name)
        if (json.has("tag_name") && !json.has("versionCode")) {
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) return null
            val cleanName = tagName.trimStart('v', 'V')
            val rawNotes = json.optString("body", "إصدار جديد متوفر على GitHub.")
                .ifBlank { "إصدار جديد يتضمن تحسينات وميزات إضافية للتطبيق." }
            val notes = formatReleaseNotesWithEmojis(rawNotes)

            var apkUrl = json.optString("html_url", DIRECT_APK_DOWNLOAD_URL)
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val dl = asset.optString("browser_download_url", "")
                    if (dl.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = dl
                        break
                    }
                }
            }

            val hasUpdate = isRemoteVersionNewer(installedCode, installedCode, cleanName, installedName)
            val latestCode = if (hasUpdate) installedCode + 1 else installedCode

            return AppUpdateInfo(
                isUpdateAvailable = hasUpdate,
                currentVersionCode = installedCode,
                currentVersionName = installedName,
                latestVersionCode = latestCode,
                latestVersionName = cleanName,
                releaseNotesAr = notes,
                telegramChannelUrl = TELEGRAM_CHANNEL_URL,
                directApkDownloadUrl = apkUrl,
                isMandatory = false,
                checkStatusMessage = if (hasUpdate) {
                    "يتوفر إصدار جديد ($cleanName) ✨"
                } else {
                    "التطبيق محدّث إلى آخر إصدار ($installedName) ✓"
                }
            )
        }

        // Standard version.json format
        if (!json.has("versionCode") && !json.has("versionName")) return null
        val latestCode = json.optInt("versionCode", installedCode)
        val latestName = json.optString("versionName", installedName)
        val rawNotes = json.optString("releaseNotes", "تحديث جديد يتضمن تحسينات وميزات إضافية للتطبيق.")
        val notes = formatReleaseNotesWithEmojis(rawNotes)
        val telegram = json.optString("telegramUrl", TELEGRAM_CHANNEL_URL)
        val download = json.optString("downloadUrl", DIRECT_APK_DOWNLOAD_URL)
        val isMandatory = json.optBoolean("isMandatory", false)

        val hasUpdate = isRemoteVersionNewer(latestCode, installedCode, latestName, installedName)

        return AppUpdateInfo(
            isUpdateAvailable = hasUpdate,
            currentVersionCode = installedCode,
            currentVersionName = installedName,
            latestVersionCode = latestCode,
            latestVersionName = latestName,
            releaseNotesAr = notes,
            telegramChannelUrl = telegram,
            directApkDownloadUrl = download,
            isMandatory = isMandatory,
            checkStatusMessage = if (hasUpdate) {
                "يتوفر إصدار جديد ($latestName) ✨"
            } else {
                "التطبيق محدّث إلى آخر إصدار ($installedName) ✓"
            }
        )
    }

    fun extractReleaseNoteItems(rawNotes: String): List<Pair<String, String>> {
        val lines = rawNotes.lines()
            .map { line ->
                line.trim()
                    .removePrefix("•")
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
            }
            .filter { it.isNotBlank() }

        return lines.map { clean ->
            val firstCp = clean.codePointAt(0)
            val isAlreadyEmoji = Character.isSupplementaryCodePoint(firstCp) ||
                Character.getType(firstCp) == Character.OTHER_SYMBOL.toInt()
            if (isAlreadyEmoji) {
                val charCount = Character.charCount(firstCp)
                val firstPart = clean.substring(0, charCount)
                // Handle optional variation selector (FE0F) right after symbol
                val restStart = if (clean.length > charCount && clean[charCount] == '\uFE0F') {
                    charCount + 1
                } else {
                    charCount
                }
                val emoji = clean.substring(0, restStart)
                val body = clean.substring(restStart).trim()
                emoji to body.ifBlank { clean }
            } else {
                pickEmojiForFeature(clean) to clean
            }
        }
    }

    fun formatReleaseNotesWithEmojis(rawNotes: String): String {
        val items = extractReleaseNoteItems(rawNotes)
        if (items.isEmpty()) return rawNotes.trim()
        return items.joinToString("\n") { (emoji, text) -> "$emoji $text" }
    }

    private fun pickEmojiForFeature(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("قبلة") || lower.contains("بوصلة") || lower.contains("qibla") || lower.contains("compass") -> "🧭"
            lower.contains("أدعية") || lower.contains("دعاء") || lower.contains("أذكار") || lower.contains("تسبيح") || lower.contains("مسبحة") -> "🤲"
            lower.contains("قرآن") || lower.contains("ورد") || lower.contains("سورة") -> "📖"
            lower.contains("workmanager") || lower.contains("مزامنة") || lower.contains("دوري") || lower.contains("تلقائي") -> "🔄"
            lower.contains("إشعار") || lower.contains("أذان") || lower.contains("تنبيه") || lower.contains("خدمة خلفية") -> "🔔"
            lower.contains("موقع") || lower.contains("gps") || lower.contains("مدينة") -> "📍"
            lower.contains("ويدجت") || lower.contains("زجاج") || lower.contains("واجهة") || lower.contains("شاشة") -> "📱"
            lower.contains("تحديث") || lower.contains("إصدار") || lower.contains("تحميل") -> "🚀"
            lower.contains("إصلاح") || lower.contains("استقرار") || lower.contains("أداء") || lower.contains("سرعة") -> "⚡"
            else -> "✨"
        }
    }

    suspend fun downloadApkDirectly(
        context: Context,
        downloadUrl: String,
        onProgress: (progress: Float, percentage: Int, progressText: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val urlsToTry = if (downloadUrl.startsWith("http://127.0.0.1") || downloadUrl.startsWith("http://localhost")) {
            listOf(downloadUrl)
        } else {
            listOf(downloadUrl, LOCAL_DEV_APK_URL)
        }

        var lastException: Exception? = null

        for (url in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SalatiApp-Android/$CURRENT_VERSION_NAME")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }

                val body = response.body ?: throw IllegalStateException("استجابة التحميل فارغة")
                val contentType = response.header("Content-Type") ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    response.close()
                    throw IllegalStateException("الرابط أعاد صفحة ويب بدلاً من ملف APK")
                }

                val totalBytes = body.contentLength().coerceAtLeast(1L)
                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val destinationFile = File(downloadDir, "salati-app-v${System.currentTimeMillis()}.apk")

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(16384)
                        var bytesReadTotal = 0L
                        var lastReportedPercentage = -1
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesReadTotal += read

                            val progress = (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val percentage = (progress * 100).toInt()

                            if (percentage != lastReportedPercentage || bytesReadTotal == totalBytes) {
                                lastReportedPercentage = percentage
                                val currentMb = String.format(Locale.US, "%.1f", bytesReadTotal / (1024f * 1024f))
                                val totalMb = String.format(Locale.US, "%.1f", totalBytes / (1024f * 1024f))
                                val text = "$currentMb ميغابايت من أصل $totalMb ميغابايت"
                                onProgress(progress, percentage, text)
                            }
                        }
                        output.flush()
                    }
                }

                val isValidApk = destinationFile.exists() && destinationFile.length() > 100_000L && try {
                    destinationFile.inputStream().use { fis ->
                        val header = ByteArray(2)
                        fis.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                    }
                } catch (_: Exception) {
                    false
                }

                if (isValidApk) {
                    onProgress(1.0f, 100, "اكتمل التنزيل بنجاح")
                    return@withContext Result.success(destinationFile)
                } else {
                    destinationFile.delete()
                    throw IllegalStateException("ملف التحديث المحمّل غير صالح أو غير مكتمل")
                }
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Download failed from $url: ${e.message}")
            }
        }

        Result.failure(lastException ?: IllegalStateException("فشل الاتصال بخادم التحديثات"))
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installation intent: ${e.message}")
        }
    }

    fun openTelegramChannel(context: Context, telegramUrl: String = TELEGRAM_CHANNEL_URL) {
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_DEEP_LINK)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch telegram URL: ${e.message}")
            }
        }
    }
}
