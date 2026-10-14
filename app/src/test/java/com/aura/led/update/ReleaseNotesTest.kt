package com.aura.led.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dialog used to print the release body as it arrives: headings, `**bold**` markers and a
 * "Full Changelog" URL in the middle of the sentences a user is meant to read. These tests pin the
 * words that survive and the syntax that does not — written from what a GitHub release body looks
 * like, not from the implementation.
 */
class ReleaseNotesTest {

    private val releaseBody = """
        ## What's new

        - The LED **no longer goes dark** when the screen locks
        - New `rainbow` animation for sender rules
        - Fixes and polish

        ### Under the hood

        The rule engine was rewritten from scratch.

        ---

        **Full Changelog**: https://github.com/example/aura/compare/v0.5.1...v0.6.0
    """.trimIndent()

    @Test
    fun `keeps the words and drops the markdown syntax`() {
        val parsed = ReleaseNotes.parse(releaseBody)

        assertEquals(
            listOf(
                ReleaseNotes.Note(ReleaseNotes.Kind.HEADING, "What's new"),
                ReleaseNotes.Note(ReleaseNotes.Kind.BULLET, "The LED no longer goes dark when the screen locks"),
                ReleaseNotes.Note(ReleaseNotes.Kind.BULLET, "New rainbow animation for sender rules"),
                ReleaseNotes.Note(ReleaseNotes.Kind.BULLET, "Fixes and polish"),
                ReleaseNotes.Note(ReleaseNotes.Kind.HEADING, "Under the hood"),
                ReleaseNotes.Note(ReleaseNotes.Kind.PARAGRAPH, "The rule engine was rewritten from scratch."),
            ),
            parsed,
        )
    }

    @Test
    fun `the changelog link at the end is not shown`() {
        val texts = ReleaseNotes.parse(releaseBody).map { it.text }

        assertTrue("the changelog link leaked into the dialog: $texts", texts.none { it.contains("github.com") })
    }

    @Test
    fun `a link keeps its text and drops its target`() {
        val parsed = ReleaseNotes.parse("- See [the setup guide](https://example.com/setup) first")

        assertEquals(listOf(ReleaseNotes.Note(ReleaseNotes.Kind.BULLET, "See the setup guide first")), parsed)
    }

    @Test
    fun `an empty or whitespace body yields nothing`() {
        assertTrue(ReleaseNotes.parse("").isEmpty())
        assertTrue(ReleaseNotes.parse("\n\n   \n").isEmpty())
    }

    @Test
    fun `a long release body is capped`() {
        val long = (1..60).joinToString("\n") { "- line $it" }

        assertEquals(ReleaseNotes.MAX_NOTES, ReleaseNotes.parse(long).size)
    }

    @Test
    fun `unicode and emoji written by the release author survive`() {
        val parsed = ReleaseNotes.parse("- Corrigé l'écran russe « Привет » 🇷🇺 et l'accent é")

        assertEquals(
            listOf(ReleaseNotes.Note(ReleaseNotes.Kind.BULLET, "Corrigé l'écran russe « Привет » 🇷🇺 et l'accent é")),
            parsed,
        )
    }
}
