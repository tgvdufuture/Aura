package com.aura.led.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update action leaves the app instead of installing anything itself.
 *
 * Aura 0.6.0–0.6.3 shipped an in-app downloader plus `REQUEST_INSTALL_PACKAGES`, and on a HyperOS 3
 * phone Play Protect refused that install with no way forward (issue #3). Nothing in the app can
 * earn the trust Play Protect withholds from a self-signed APK, but the permission that made Aura
 * look like an installer can go: the browser that installed Aura already holds the "install unknown
 * apps" grant, so the dialog opens the release page. These read the sources as text for the same
 * reason the other update guards do — the flow needs a device — and they are the check that the
 * permission really left the app rather than just the call sites that used it.
 */
class UpdateActionTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()
    private val manifest = source("app/src/main/AndroidManifest.xml").readText()

    @Test
    fun `the update action opens the release page`() {
        val startUpdate = ui
            .substringAfter("fun startUpdate()")
            .substringBefore("fun openReleasePage()")

        assertTrue(
            "startUpdate does not open the release page: the button either does nothing or is back " +
                "to downloading an APK it cannot install",
            startUpdate.contains("openReleasePage()"),
        )
        assertTrue(
            "the release page is not handed to a browser, so nothing would download the APK",
            ui.contains("Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL))"),
        )
    }

    @Test
    fun `the dialog offers one action, wired to the release page`() {
        val dialog = ui
            .substringAfter("private fun UpdateDialog(")
            .substringBefore("/** One line of a release body")

        assertTrue(
            "the primary button is not the update action",
            dialog.contains("onClick = onUpdate"),
        )
        assertTrue(
            "the primary button has no localised copy",
            dialog.contains("R.string.update_download"),
        )
        assertFalse(
            "a permission screen is still offered: there is no permission left to grant",
            dialog.contains("update_permission_needed"),
        )
        assertFalse(
            "a retry loop is still offered: opening a browser page either works or toasts",
            dialog.contains("update_retry"),
        )
    }

    @Test
    fun `no download or install machinery is left in the app`() {
        val leftovers = listOf(
            "ApkInstaller",
            "ApkDownloader",
            "DownloadFailure",
            "canRequestPackageInstalls",
            "REQUEST_INSTALL_PACKAGES",
        ).filter { ui.contains(it) || manifest.contains(it) }

        assertTrue(
            "the update flow still reaches an installer through: $leftovers",
            leftovers.isEmpty(),
        )
    }

    @Test
    fun `the manifest exposes no APK provider`() {
        assertFalse(
            "a FileProvider is still declared: nothing in the app hands out APK URLs any more",
            manifest.contains("FileProvider"),
        )
        assertFalse(
            "res/xml/file_paths.xml is back, so a provider has something to expose",
            File("app/src/main/res/xml/file_paths.xml").exists() ||
                File("../app/src/main/res/xml/file_paths.xml").exists(),
        )
    }

    @Test
    fun `no download or install copy is left behind`() {
        val stale = listOf(
            "update_downloading",
            "update_permission_needed",
            "update_size",
            "update_install",
            "update_retry",
            "update_hide",
            "update_failed_body",
            "update_failed_manual",
        )
        for (locale in listOf("values", "values-fr", "values-ru")) {
            val strings = source("app/src/main/res/$locale/strings.xml").readText()
            val found = stale.filter { strings.contains("name=\"$it\"") }

            assertTrue(
                "$locale still carries copy for a step that no longer exists: $found",
                found.isEmpty(),
            )
        }
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
