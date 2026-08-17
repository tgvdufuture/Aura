package com.aura.led.notification

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Recovers the full (unredacted) notification title/text when Android hides
 * sensitive content on the lock screen.
 *
 * While the device is locked with "hide sensitive notification content" enabled,
 * [android.service.notification.NotificationListenerService] receives the
 * notification's *public* version with EXTRA_TITLE/EXTRA_TEXT stripped, so
 * contact/group rules can't be resolved and only the static app color is emitted.
 * Since the LED itself is already driven through Shizuku (shell UID), we read the
 * real content the same way: `dumpsys notification --noredact`.
 */
object FullNotificationReader {

    private const val TAG = "AuraFullNotif"
    private const val DUMPSYS_TIMEOUT_MS = 3_000L

    data class Content(val title: String?, val text: String?)

    /** Reads the most recent notification content for [pkg], or null if unavailable. */
    fun readLatest(pkg: String): Content? {
        if (!Shizuku.pingBinder()) return null
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val proc = service.newProcess(
                arrayOf("dumpsys", "notification", "--noredact"),
                null,
                null,
            ) ?: return null

            val output = CompletableFuture.supplyAsync {
                ParcelFileDescriptor.AutoCloseInputStream(proc.getInputStream())
                    .bufferedReader()
                    .use { it.readText() }
            }
            val text = try {
                output.get(DUMPSYS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "dumpsys timed out", e)
                runCatching { proc.destroy() }
                null
            }
            text?.let { parse(it, pkg) }
        }.onFailure { Log.w(TAG, "readLatest failed", it) }.getOrNull()
    }

    internal fun parse(dump: String, pkg: String): Content? {
        var currentPkg: String? = null
        var title: String? = null
        var text: String? = null
        var found = false

        for (line in dump.lineSequence()) {
            val header = RECORD_HEADER.find(line)
            if (header != null) {
                currentPkg = header.groupValues[1]
                if (currentPkg == pkg) {
                    found = true
                    title = null
                    text = null
                }
                continue
            }
            if (currentPkg != pkg) continue
            val trimmed = line.trim()
            when {
                trimmed.startsWith("android.title=") ->
                    title = parseValue(trimmed.substringAfter("android.title="))
                trimmed.startsWith("android.text=") ->
                    text = parseValue(trimmed.substringAfter("android.text="))
            }
        }
        if (!found) return null
        return Content(title, text)
    }

    private val RECORD_HEADER = Regex("""NotificationRecord\(.*\bpkg=([^\s)]+)""")

    /**
     * Handles both `CharSequence(className=..., text=Mom)` and plain `Mom` dump formats.
     */
    private fun parseValue(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty() || value == "null") return null
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
        }
        val textMarker = ", text="
        if (value.contains(textMarker)) {
            var text = value.substringAfter(textMarker)
            // Content is wrapped as CharSequence(className=..., text=CONTENT); the last
            // ')' closes the CharSequence, not the CONTENT (which may contain ')' itself).
            val end = text.lastIndexOf(')')
            if (end >= 0) text = text.substring(0, end)
            text = text.trim()
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length - 1)
            }
            return text.takeIf { it.isNotEmpty() && it != "null" }
        }
        return value
    }
}
