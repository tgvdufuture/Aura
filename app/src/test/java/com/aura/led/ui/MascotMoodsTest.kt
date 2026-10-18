package com.aura.led.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mascot's invisible failure mode: a state wearing a face that says the opposite of what
 * happened. An error dialog with the laughing mascot compiles, looks like a mascot, and lies.
 *
 * Only one state is left since the update action became "open the release page", so this now pins
 * that the dialog keeps the good-news face — and keeps the mapping in one total `when`, which is
 * what a compiler checks when a failure state comes back.
 */
class MascotMoodsTest {

    @Test
    fun `each update state wears the face that says what happened`() {
        assertEquals(MascotMood.JOY, mascotFor(MainViewModel.UpdatePhase.AVAILABLE))
        assertEquals(MascotMood.JOY, mascotFor(MainViewModel.UpdatePhase.HIDDEN))
    }

    @Test
    fun `every face has its own artwork`() {
        val arts = MascotMood.values().map { it.art }
        assertTrue("a face has no drawable", arts.none { it == 0 })
        assertEquals("two faces share one file", arts.size, arts.distinct().size)
    }
}
