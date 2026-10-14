package com.aura.led.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mascot's invisible failure mode: a state wearing a face that says the opposite of what
 * happened. An error dialog with the laughing mascot compiles, looks like a mascot, and lies.
 */
class MascotMoodsTest {

    @Test
    fun `each update state wears the face that says what happened`() {
        assertEquals(MascotMood.JOY, mascotFor(MainViewModel.UpdatePhase.AVAILABLE))
        assertEquals(MascotMood.JOY, mascotFor(MainViewModel.UpdatePhase.DOWNLOADING))
        assertEquals(MascotMood.WHAT, mascotFor(MainViewModel.UpdatePhase.PERMISSION_REQUIRED))
        assertEquals(MascotMood.OH, mascotFor(MainViewModel.UpdatePhase.ERROR))
        assertEquals(MascotMood.JOY, mascotFor(MainViewModel.UpdatePhase.HIDDEN))
    }

    @Test
    fun `a failure never wears the face of good news`() {
        assertNotEquals(mascotFor(MainViewModel.UpdatePhase.ERROR), mascotFor(MainViewModel.UpdatePhase.AVAILABLE))
        assertNotEquals(
            mascotFor(MainViewModel.UpdatePhase.ERROR),
            mascotFor(MainViewModel.UpdatePhase.DOWNLOADING),
        )
    }

    @Test
    fun `every face has its own artwork`() {
        val arts = MascotMood.values().map { it.art }
        assertTrue("a face has no drawable", arts.none { it == 0 })
        assertEquals("two faces share one file", arts.size, arts.distinct().size)
    }
}
