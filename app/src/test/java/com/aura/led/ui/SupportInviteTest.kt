package com.aura.led.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app-list banner is the only surface that reaches a user who never opens Settings, so what it
 * links to decides whether it does anything at all.
 *
 * Its first version offered a donation and nothing else, which is exactly backwards: the users it
 * exists for are the ones who have not answered the support question, and a donation button leaves
 * them unasked. Two properties make it work, and both fail silently — a button with no destination
 * still compiles, and a link that opens Settings at the top still looks like it worked. This project
 * has no `compose-ui-test` and no instrumented source set, so they are pinned as text, the technique
 * `UpdatePermissionTest` already uses for wiring that needs a device.
 */
class SupportInviteTest {

    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()
    private val activity = source("app/src/main/java/com/aura/led/MainActivity.kt").readText()

    /**
     * The settings list itself, from the screen's declaration to the next screen in the file.
     *
     * The two properties below — where the question sits in the list, and how the link reaches it —
     * can only be checked inside this function, and both are the kind that fail on the device while
     * the test suite stays green.
     */
    private val settings: String =
        ui.substringAfter("fun SettingsScreen(")
            .substringBefore("fun ConsentScreen(")

    /**
     * [settings] without its `//` comment lines.
     *
     * The rule that rejects `bringIntoView` has to see code only: the comment above the scroll
     * names the mechanism it replaces, on purpose, so that the next reader knows why it is not used
     * — and a check that fires on prose would make explaining a decision impossible.
     */
    private val settingsCode: String =
        settings.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    /**
     * The banner's own body, from its declaration to the first `}` at column 0.
     *
     * Written against `\n}` and not `\n}\n`: these files are checked out with CRLF endings, so a
     * rule that expects a bare newline *after* the brace never matches and the extraction silently
     * runs to the end of the file — which is how this test first "proved" the banner carried the
     * card it does not carry.
     */
    private val banner: String =
        ui.substringAfter("private fun SupportInviteBanner")
            .substringBefore("\n}")

    @Test
    fun `the banner offers the question first and the donation second`() {
        assertTrue("the banner was not found, or it was renamed", banner.length > 200)

        val question = banner.indexOf("onAnswerQuestion")
        val donation = banner.indexOf("onDonate")

        assertTrue("the banner no longer offers the question at all", question >= 0)
        assertTrue("the donation path is gone, and it is the support path that exists today", donation >= 0)
        assertTrue(
            "the question has to be the primary action: a banner that only sells a donation leaves " +
                "the users it exists for unasked",
            question < donation,
        )
        assertTrue(
            "the banner must not carry the four-answer card itself — on the device it measured " +
                "446 dp, 53 % of the screen, with the whole app list below the fold",
            !banner.contains("SupportQuestionCard"),
        )
    }

    @Test
    fun `following the question lands on the question, not at the top of Settings`() {
        assertTrue(
            "the banner's primary action must be the one that focuses the question",
            ui.contains("onAnswerQuestion = onOpenSupportQuestion"),
        )
        assertTrue(
            "Settings must accept the focus flag",
            ui.contains("focusSupport: Boolean = false"),
        )
        assertTrue(
            "Settings must scroll its own list to the question: the card sits below six others, " +
                "and opening at the top leaves it entirely off screen",
            settings.contains("scrollToItem("),
        )
        assertTrue(
            "not `bringIntoViewRequester`: a LazyColumn never composes the items below the fold, " +
                "so the requester is attached to nothing and the request is dropped in silence — " +
                "which is how this link first shipped, landing the user at the top of Settings",
            !settingsCode.contains("bringIntoView"),
        )
        assertTrue(
            "the scroll goes to the list's last item, so nothing may be laid out after the " +
                "question card — a card added below it would move the link's landing place",
            !settings.substringAfter("SupportQuestionCard(").contains("item {"),
        )
        assertTrue(
            "and the list has to be the one that scrolls: a scroll state on the screen scrolls " +
                "nothing",
            settings.contains("rememberLazyListState()") && settings.contains("state = listState,"),
        )
    }

    /**
     * The two ways into Settings must not collapse into one: the banner wants the question, the gear
     * wants the top of the list, and a stale flag would send the gear to the bottom.
     */
    @Test
    fun `the gear still opens Settings at the top`() {
        assertTrue(
            "opening Settings from the app list must clear the focus flag",
            activity.contains("onOpenSettings = { showSettings = true; focusSupport = false }"),
        )
        assertTrue(
            "the banner's action must set it",
            activity.contains("onOpenSupportQuestion = { showSettings = true; focusSupport = true }"),
        )
        assertTrue(
            "Settings must receive it",
            activity.contains("focusSupport = focusSupport,"),
        )
        assertTrue(
            "leaving Settings must clear it, or the next entry scrolls on its own",
            activity.contains("onBack = { showSettings = false; focusSupport = false }"),
        )
    }

    /** Resolves a repository path from the module working directory Gradle uses for unit tests. */
    private fun source(path: String): File {
        val candidates = listOf(File(path), File("../$path"))

        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found (looked in ${candidates.joinToString()})")
    }
}
