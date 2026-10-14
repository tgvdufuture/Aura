package com.aura.led

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android falls back to the default locale for any string a translation file omits, so a missing
 * French or Russian entry is not a crash — it is English copy inside a French screen, which nobody
 * notices until a release. This asserts the three shipped locales stay in step.
 */
class StringResourceParityTest {

    private val english = stringsOf("values")
    private val french = stringsOf("values-fr")
    private val russian = stringsOf("values-ru")

    private val locales = listOf("values" to english, "values-fr" to french, "values-ru" to russian)

    /** Strings this feature added or renamed: the copy only exists if all three locales have it. */
    private val featureKeys = setOf(
        "support_title",
        "support_intro",
        "support_donate",
        "support_offer_title",
        "support_offer_items",
        "support_question",
        "support_answer_donation",
        "support_answer_one_time",
        "support_answer_subscription",
        "support_answer_nothing",
        "support_answer_saved",
        "consent_funding",
        "language_subtitle",
        "update_notes_title",
        "update_hide",
        "empty_no_enabled_title",
        "empty_no_enabled_hint",
        "settings_attention_important",
        "settings_attention_info",
    )

    /** The labels of the support question: the copy a user reads before answering. */
    private val answerKeys = listOf(
        "support_answer_donation",
        "support_answer_one_time",
        "support_answer_subscription",
        "support_answer_nothing",
    )

    @Test
    fun `french and russian declare the same strings as english`() {
        assertEquals("strings missing from values-fr", emptySet<String>(), english - french)
        assertEquals("strings missing from values-ru", emptySet<String>(), english - russian)
        assertEquals("strings declared only in values-fr", emptySet<String>(), french - english)
        assertEquals("strings declared only in values-ru", emptySet<String>(), russian - english)
    }

    @Test
    fun `every string this feature introduced exists in all three locales`() {
        for (locale in locales) {
            assertEquals("missing from ${locale.first}", emptySet<String>(), featureKeys - locale.second)
        }
    }

    @Test
    fun `the withdrawn premium copy is gone from every locale`() {
        val leftovers = (english + french + russian).filter { it.startsWith("premium_") }

        assertTrue("premium_* strings still declared: $leftovers", leftovers.isEmpty())
    }

    /**
     * Owner feedback, 2026-10-14: "Ni l'un ni l'autre" left the user asking "neither of what?" —
     * an answer written as the negation of the other answers, on a card that offers four of them.
     * Each label has to say what it means on its own, whatever the question above it says.
     */
    @Test
    fun `no support answer is worded by contrast with the other answers`() {
        val contrast = listOf("l'un", "l'autre", "les deux", "neither", "either", "ни то", "ни другое")

        for ((qualifier, _) in locales) {
            for (key in answerKeys) {
                val label = stringValue(qualifier, key).replace('’', '\'').lowercase()
                val found = contrast.filter { label.contains(it) }

                assertTrue(
                    "$qualifier/$key only means something next to the other answers: \"$label\"",
                    found.isEmpty(),
                )
            }
        }
    }

    /** Reads the declared `name` attributes of one `values` directory. */
    private fun stringsOf(qualifier: String): Set<String> =
        Regex("<string name=\"([^\"]+)\"")
            .findAll(stringsFile(qualifier).readText())
            .map { it.groupValues[1] }
            .toSet()

    /** Reads the text of one declared string, or fails when the locale does not declare it. */
    private fun stringValue(qualifier: String, name: String): String =
        Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(stringsFile(qualifier).readText())
            ?.groupValues
            ?.get(1)
            ?: error("$name is not declared in $qualifier")

    private fun stringsFile(qualifier: String): File {
        val candidates = listOf(
            File("src/main/res/$qualifier/strings.xml"),
            File("app/src/main/res/$qualifier/strings.xml"),
        )

        return candidates.firstOrNull { it.isFile }
            ?: error("strings.xml not found in $qualifier (looked in ${candidates.joinToString()})")
    }
}
