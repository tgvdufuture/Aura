package com.aura.led.update

import com.aura.led.analytics.AnalyticsEvents
import com.aura.led.analytics.AnalyticsSanitizer
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reason a failed update reports has to survive two doors before it is readable: the sanitizer,
 * which drops anything not in the allow-list, and the vocabulary itself, which is what a breakdown
 * in the dashboard groups by.
 *
 * Why this exists at all: on 2026-10-16 the only accepted update in the field failed at the
 * download, and `update_download_result` said `result=error` with no reason — the cause was on one
 * phone's logcat and nowhere else, so the failure could not be acted on. The event now carries
 * `reason`, and these tests are what keeps it from degrading back into free text.
 */
class DownloadFailureTest {

    @Test
    fun `a refusal keeps its own reason, and anything else is a network failure`() {
        DownloadFailure.ALL.forEach { reason ->
            assertEquals(
                "a $reason failure has to report itself, not the fallback",
                reason,
                DownloadFailure.reasonOf(DownloadFailure(reason, "detail for logcat")),
            )
        }

        // Fail-closed on purpose: a throwable the downloader did not classify cannot be told apart
        // from a timeout after the fact, and "network" says so instead of guessing.
        assertEquals(DownloadFailure.NETWORK, DownloadFailure.reasonOf(IOException("http 500")))
        assertEquals(DownloadFailure.NETWORK, DownloadFailure.reasonOf(SocketTimeoutException("Read timed out")))
        assertEquals(DownloadFailure.NETWORK, DownloadFailure.reasonOf(RuntimeException()))
    }

    @Test
    fun `every reason the dashboard can receive is in the vocabulary`() {
        assertEquals(
            "a constant missing from ALL is a constant the guide and these tests cannot see",
            setOf(
                DownloadFailure.HTTP,
                DownloadFailure.TOO_SMALL,
                DownloadFailure.UNREADABLE,
                DownloadFailure.WRONG_PACKAGE,
                DownloadFailure.NOT_NEWER,
                DownloadFailure.NETWORK,
            ),
            DownloadFailure.ALL,
        )
        assertTrue(
            "the vocabulary has to stay small and readable as a breakdown",
            DownloadFailure.ALL.size <= 8,
        )
        assertTrue(
            "a reason is a dashboard label: lowercase words, no spaces, no punctuation",
            DownloadFailure.ALL.all { it.matches(Regex("[a-z]{3,12}(_[a-z]{3,8})?")) },
        )
    }

    @Test
    fun `the reason survives both allow-lists and travels with the failure`() {
        for (reason in DownloadFailure.ALL) {
            val sent = AnalyticsSanitizer.sanitize(
                mapOf(
                    AnalyticsEvents.P_RESULT to "error",
                    AnalyticsEvents.P_LATEST_TAG to "v0.6.2",
                    AnalyticsEvents.P_REASON to DownloadFailure.reasonOf(
                        DownloadFailure(reason, "downloaded version 11 <= installed 12"),
                    ),
                ),
            )

            assertEquals(
                "the reason is dropped before it leaves the device, so the event says nothing again",
                mapOf("result" to "error", "latest_tag" to "v0.6.2", "reason" to reason),
                sent,
            )
        }
    }

    @Test
    fun `the exception message is never what gets reported`() {
        val detail = "unexpected package com.example.something.very.long"
        val failure = DownloadFailure(DownloadFailure.WRONG_PACKAGE, detail)

        assertEquals(DownloadFailure.WRONG_PACKAGE, DownloadFailure.reasonOf(failure))
        assertFalse(
            "the reason must not be derived from the message",
            DownloadFailure.reasonOf(failure).contains("com.example"),
        )
    }
}
