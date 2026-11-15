package com.example.model

/**
 * Model representing remote and local application update information.
 */
data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val isCheckFailed: Boolean = false,
    val isDevServerAuthLocked: Boolean = false,
    val currentVersionCode: Int = 10,
    val currentVersionName: String = "1.4.6",
    val latestVersionCode: Int = 10,
    val latestVersionName: String = "1.4.6",
    val releaseNotesAr: String = "",
    val telegramChannelUrl: String = "https://t.me/AS3SA",
    val directApkDownloadUrl: String = "https://ais-dev-u3bbyddierfnvqnk2dov33-152810655533.europe-west2.run.app/salati-app.apk",
    val isMandatory: Boolean = false,
    val isPreview: Boolean = false,
    val checkStatusMessage: String = "",

    // In-App Direct Download State (Progress from 0% to 100%)
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadPercentage: Int = 0,
    val downloadProgressText: String = "",
    val isDownloadComplete: Boolean = false,
    val downloadedApkPath: String? = null,
    val downloadErrorMessage: String? = null
)
