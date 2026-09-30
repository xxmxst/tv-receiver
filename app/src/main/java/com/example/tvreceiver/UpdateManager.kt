package com.example.tvreceiver

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class UpdateManager(private val context: Context) {

    data class ReleaseInfo(
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    private val appContext = context.applicationContext
    private var downloadId: Long? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (finishedId <= 0L || finishedId != downloadId) return
            installDownloadedApk()
        }
    }

    fun register() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }

    fun unregister() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    fun fetchLatestRelease(): ReleaseInfo? {
        return runCatching {
            val conn = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "tv-receiver/${currentVersionName()}")
            conn.inputStream.bufferedReader().use { reader ->
                parseRelease(JSONObject(reader.readText()))
            }
        }.getOrNull()
    }

    fun isNewerThanCurrent(release: ReleaseInfo): Boolean {
        return compareVersions(
            normalizeVersion(release.versionName),
            normalizeVersion(currentVersionName())
        ) > 0
    }

    fun startUpdateDownload(release: ReleaseInfo): Boolean {
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return false
        val request = DownloadManager.Request(Uri.parse(release.apkUrl)).apply {
            setTitle("tv-receiver ${release.versionName}")
            setDescription("正在下载新版本")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                APK_FILE_NAME
            )
        }
        downloadId = manager.enqueue(request)
        return true
    }

    private fun installDownloadedApk() {
        val file = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (!file.exists()) {
            Toast.makeText(appContext, "更新包不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(appContext, "未找到可安装 APK 的应用", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name").lowercase(Locale.US)
            if (name.endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        if (apkUrl.isNullOrBlank()) return null
        return ReleaseInfo(
            versionName = json.optString("tag_name").ifBlank { json.optString("name") },
            apkUrl = apkUrl,
            notes = json.optString("body")
        )
    }

    private fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V")
    }

    fun currentVersionName(): String {
        return runCatching {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            packageInfo.versionName
        }.getOrNull().orEmpty().ifBlank { "0.0.0" }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.')
        val rightParts = right.split('.')
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val leftValue = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
            val rightValue = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
            if (leftValue != rightValue) return leftValue.compareTo(rightValue)
        }
        return 0
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/qq244901796/tv-receiver/releases/latest"
        private const val APK_FILE_NAME = "tv-receiver-latest.apk"
    }
}
