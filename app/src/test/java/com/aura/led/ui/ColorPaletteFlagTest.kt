package com.aura.led.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `is_custom` answers one question — is this colour one the app offers, or one the user had to go
 * and pick? — and it answered it for only one of the two palettes.
 *
 * The compact row on an app card offers [PALETTE] (8 colours), the custom picker offers
 * `MAIN_COLOR_SWATCHES` (20 colours), and the two lists share no colour. The flag compared
 * `MAIN_COLOR_SWATCHES` alone, so every tap on the compact row was reported as custom: measured on
 * the live project on 2026-10-18, 180 of the 183 `app_rule_color_changed` events were tagged
 * custom, which is the opposite of what the G3 "custom colours" insight is meant to read.
 *
 * Unlike the analytics call sites that need a device, this flag is a pure function, so it is
 * checked here rather than pinned as text.
 */
class ColorPaletteFlagTest {

    @Test
    fun `a compact palette colour is not custom`() {
        PALETTE.forEach { hex ->
            assertFalse(
                "$hex is offered by the compact row on every app card and must not count as custom",
                isCustomColor(hex),
            )
        }
    }

    @Test
    fun `a picker palette colour is not custom`() {
        MAIN_COLOR_SWATCHES.forEach { hex ->
            assertFalse(
                "$hex is offered by the custom picker's own palette and must not count as custom",
                isCustomColor(hex),
            )
        }
    }

    @Test
    fun `the palette comparison does not depend on the casing of the hex`() {
        assertFalse("a palette colour in upper case is still a palette colour", isCustomColor("#FFD61D"))
        assertFalse(isCustomColor(PALETTE.first().uppercase()))
    }

    @Test
    fun `a colour neither palette offers is custom`() {
        listOf("#123456", "#abcdef", "#000001").forEach { hex ->
            assertTrue("$hex is in no palette and has to count as custom", isCustomColor(hex))
        }
    }

    @Test
    fun `the two palettes are both non-empty, or the test above proves nothing`() {
        assertTrue("the compact palette is empty: the check has nothing to compare", PALETTE.isNotEmpty())
        assertTrue(
            "the picker palette is empty: the check has nothing to compare",
            MAIN_COLOR_SWATCHES.isNotEmpty(),
        )
    }
}
