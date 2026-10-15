package com.aura.led.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A failed update has to say something a user can act on, in their own language.
 *
 * What the device showed, on a French screen: `Échec de la mise à jour : unexpected package
 * com.aura.led` — an English exception message, for a failure that retrying cannot fix, with
 * "Retry" as the only action. The reason now goes to logcat and the copy comes from the three
 * locales, which `StringResourceParityTest` keeps in step. The rendering itself needs a device, so
 * these read the call sites as text, as the other update-flow guards do.
 */
class UpdateFailureUiTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()

    @Test
    fun `the dialog never prints the raw failure text`() {
        assertFalse(
            "the dialog is rendering the raw exception again: the reason belongs in logcat",
            ui.contains("state.error"),
        )
        assertTrue(
            "the failure message has to come from the localized copy",
            ui.contains("R.string.update_failed_body"),
        )
    }

    @Test
    fun `a failure that retrying cannot fix offers the release page`() {
        assertTrue(
            "no manual download action: a wrong package or a truncated file stays retry-only, and " +
                "the user is stuck on a failure that repeats",
            ui.contains("R.string.update_failed_manual"),
        )
        assertTrue(
            "the manual download action is not wired to anything",
            ui.contains("onOpenReleasePage = viewModel::openReleasePage"),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
