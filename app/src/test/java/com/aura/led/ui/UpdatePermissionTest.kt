package com.aura.led.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the path back from the system's "install unknown apps" screen.
 *
 * Found on the device, not here: after granting the permission and returning to Aura, the update
 * dialog still showed "Open settings" and **nothing that would download the update**, while the copy
 * on that same screen says "then start the update again". Closing the dialog at that point also
 * marked the release as dismissed, so the update was gone for good.
 *
 * The real transition needs a `PackageManager` and a system screen, which this project has no
 * instrumented tests for, so the wiring is pinned as text — the technique `StringResourceParityTest`
 * already uses for the copy. What this cannot check is the rendering itself: the device pass is what
 * proved the dialog offers the download button again.
 */
class UpdatePermissionTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()

    @Test
    fun `returning to the app re-reads the install permission`() {
        assertTrue(
            "nothing re-reads the install permission on resume: a user who just granted it is left " +
                "with no way to download the update",
            ui.contains("viewModel.refreshInstallPermission()"),
        )
    }

    @Test
    fun `the install permission is re-checked, not assumed`() {
        assertTrue(
            "refreshInstallPermission has to ask Android whether the install is allowed now",
            ui.contains("if (ApkInstaller.canInstall(ctx()))"),
        )
        assertTrue(
            "the dialog has to come back to AVAILABLE — the phase that offers the download — rather " +
                "than starting one on its own or staying stuck",
            ui.contains("copy(phase = UpdatePhase.AVAILABLE)"),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
