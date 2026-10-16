package com.aura.led.update

import java.io.IOException

/**
 * Why a download, or the APK it produced, was refused.
 *
 * The message is for logcat and for the user's own screen only ("downloaded version 10 <= installed
 * 12"); what leaves the phone is [reason], one of the constants below and nothing else. An
 * exception's message is free text, and free text is the one thing this pipeline cannot make safe
 * (see `AnalyticsSanitizer`, which drops any string over 64 characters but cannot tell a package
 * name from a stack trace).
 *
 * The vocabulary is deliberately the *shape* of the failure rather than its detail: `http` says the
 * server answered something other than 200, `too_small` says the transfer stopped half-way, and the
 * three validation cases say the file arrived but is not a newer Aura. That is what separates "the
 * release is broken" from "this phone's network is" — the question
 * `update_download_result{result=error}` could not answer on 2026-10-16, when the only accepted
 * update in the field failed and the cause lived on one phone's logcat.
 */
class DownloadFailure(
    val reason: String,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {

    companion object {
        /** The server answered with a status other than 200. */
        const val HTTP = "http"

        /** The transfer ended before the expected size, or produced too few bytes to be an APK. */
        const val TOO_SMALL = "too_small"

        /** The file could not be read as an APK at all. */
        const val UNREADABLE = "unreadable"

        /** An APK, but not Aura's. */
        const val WRONG_PACKAGE = "wrong_package"

        /** Aura's own APK, at a version that is not newer than the installed one. */
        const val NOT_NEWER = "not_newer"

        /** Everything else: timeouts, dropped connections, unreadable storage. */
        const val NETWORK = "network"

        /** Every reason the dashboard can receive. Read by `DownloadFailureTest` and the guide. */
        val ALL: Set<String> = setOf(HTTP, TOO_SMALL, UNREADABLE, WRONG_PACKAGE, NOT_NEWER, NETWORK)

        /**
         * The reason to report for [error].
         *
         * Anything that is not one of ours is reported as [NETWORK]: a throwable from the connection
         * or the storage layer cannot be told apart after the fact, and reporting "unknown" instead
         * of guessing is what keeps the values readable — a reason that is right half the time is
         * worse than no reason at all.
         */
        fun reasonOf(error: Throwable): String = (error as? DownloadFailure)?.reason ?: NETWORK
    }
}
