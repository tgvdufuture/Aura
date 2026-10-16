package com.aura.led.update

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the release APK into app-private storage (no storage permission needed) and
 * refuses to hand anything to the installer unless it really is a newer build of Aura.
 *
 * Android additionally refuses to install an APK that is not signed with the app's own
 * key, so a tampered file cannot replace the installed app.
 */
object ApkDownloader {
    private const val TAG = "AuraUpdate"
    private const val TIMEOUT_MS = 15_000
    private const val MIN_VALID_BYTES = 1_000_000L
    private const val PROGRESS_STEP_PERCENT = 2

    suspend fun download(
        context: Context,
        url: String,
        expectedSizeBytes: Long,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runDownload(context, url, expectedSizeBytes, onProgress)
    }

    private fun runDownload(
        context: Context,
        url: String,
        expectedSizeBytes: Long,
        onProgress: (Int) -> Unit,
    ): Result<File> {
        val directory = File(context.getExternalFilesDir(null), "update").apply { mkdirs() }
        val target = File(directory, "app-release.apk")
        if (target.exists()) target.delete()

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "Aura-Android")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return Result.failure(DownloadFailure(DownloadFailure.HTTP, "http $code"))
            }
            val total = expectedSizeBytes.takeIf { it > 0 } ?: connection.contentLengthLong

            var copied = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                            if (percent >= lastPercent + PROGRESS_STEP_PERCENT || percent == 100) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            if (target.length() < MIN_VALID_BYTES) {
                target.delete()
                return Result.failure(DownloadFailure(DownloadFailure.TOO_SMALL, "downloaded file too small"))
            }
            validate(context, target)?.let { error ->
                target.delete()
                return Result.failure(error)
            }
            Result.success(target)
        } catch (e: Exception) {
            target.delete()
            // Anything thrown this far is the connection or the storage: none of the refusal cases
            // above throw, they return. Reported as NETWORK, with the message kept for logcat.
            val failure = e as? DownloadFailure
                ?: DownloadFailure(DownloadFailure.NETWORK, e.message ?: e.javaClass.simpleName, e)
            Log.w(TAG, "download failed", failure)
            Result.failure(failure)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** Returns the reason the file cannot be installed, or null when it is a valid newer APK. */
    private fun validate(context: Context, apk: File): DownloadFailure? {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return DownloadFailure(DownloadFailure.UNREADABLE, "unreadable apk")
        if (info.packageName != context.packageName) {
            return DownloadFailure(
                DownloadFailure.WRONG_PACKAGE,
                "unexpected package ${info.packageName}",
            )
        }
        val downloadedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") info.versionCode
        }
        val installedCode = @Suppress("DEPRECATION") if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }
        if (downloadedCode <= installedCode) {
            return DownloadFailure(
                DownloadFailure.NOT_NEWER,
                "downloaded version $downloadedCode <= installed $installedCode",
            )
        }
        return null
    }
}
