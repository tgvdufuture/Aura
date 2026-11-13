package com.aura.led.notification

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The listener's half of issue #4: the reconnect path is wired to [RebindBackoff] and resets once
 * the listener is back. This project has no instrumented source set, so the wiring is read as text —
 * the same technique `QuietHoursWiringTest` and `LedTestWarningTest` already use.
 */
class RebindWiringTest {

    private val listener = listenerSource().readText()

    @Test
    fun `a disconnect waits the backoff, not a fixed delay`() {
        assertTrue(
            "the listener no longer asks the backoff rule how long to wait",
            listener.contains("val delayMs = RebindBackoff.delayMs(rebindAttempt)"),
        )
        assertTrue(
            "the request is not posted with the delay the rule returned",
            listener.contains("rebindHandler.postDelayed(rebindRunnable, delayMs)"),
        )
        assertFalse(
            "the fixed two-second retry is back: a flapping listener would wake the phone every 2 s " +
                "for as long as it is on",
            listener.contains("REBIND_DELAY_MS"),
        )
    }

    @Test
    fun `the attempt count grows on each disconnect`() {
        val disconnect = listener.substringAfter("override fun onListenerDisconnected()")
            .substringBefore("override fun onNotificationPosted")
        assertTrue("the attempt is never counted", disconnect.contains("rebindAttempt++"))
    }

    @Test
    fun `a successful connection starts the backoff over`() {
        val connect = listener.substringAfter("override fun onListenerConnected()")
            .substringBefore("override fun onListenerDisconnected()")
        assertTrue(
            "without the reset, one bad minute caps every later retry at five minutes",
            connect.contains("rebindAttempt = 0"),
        )
    }

    private fun listenerSource(): File {
        val path = "app/src/main/java/com/aura/led/notification/AuraNotificationListener.kt"
        return listOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found")
    }
}
