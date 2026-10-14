package com.aura.led.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface CheckResult {
    data object UpToDate : CheckResult

    data class UpdateAvailable(
        val tag: String,
        val versionName: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val notes: String,
    ) : CheckResult

    data class Failed(val reason: String) : CheckResult
}

/**
 * Asks the public GitHub Releases API for the latest release.
 *
 * No identifier is sent: only the app's own version, as the HTTP `User-Agent`. Uses
 * `HttpURLConnection` and `org.json`, both provided by the platform, so the update path
 * adds no dependency.
 */
object UpdateChecker {
    private const val TAG = "AuraUpdate"
    private const val RELEASES_URL = "https://api.github.com/repos/tgvdufuture/Aura/releases/latest"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_NOTES_LENGTH = 800

    suspend fun check(installedVersionName: String, userAgentVersion: String): CheckResult =
        withContext(Dispatchers.IO) { runCheck(installedVersionName, userAgentVersion) }

    private fun runCheck(installedVersionName: String, userAgentVersion: String): CheckResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Aura/$userAgentVersion")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return CheckResult.Failed("http $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").takeIf { it.isNotBlank() }
                ?: return CheckResult.Failed("missing tag_name")

            if (!VersionComparison.isNewer(installedVersionName, tag)) {
                return CheckResult.UpToDate
            }

            val asset = firstApkAsset(json)
                ?: return CheckResult.Failed("no apk asset")

            CheckResult.UpdateAvailable(
                tag = tag,
                versionName = tag.removePrefix("v"),
                apkUrl = asset.first,
                sizeBytes = asset.second,
                notes = json.optString("body").orEmpty().take(MAX_NOTES_LENGTH),
            )
        } catch (e: IOException) {
            CheckResult.Failed(e.message ?: "io error")
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: e::class.java.simpleName)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun firstApkAsset(json: JSONObject): Pair<String, Long>? {
        val assets = json.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.isBlank()) continue
            return url to asset.optLong("size")
        }
        return null
    }

    /** Logged helper so call sites keep one log tag for the update flow. */
    fun logFailure(reason: String) {
        Log.d(TAG, "update check failed: $reason")
    }
}
