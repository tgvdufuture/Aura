package com.aura.led.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a tap on the update notification rebuilds the update screen from.
 *
 * One process writes the record, another reads it, so the rules that matter here are the ones that
 * keep a short or foreign record off the update screen. The JSON codec that used to carry it was
 * replaced when the unit tests proved `org.json` is stubbed on the JVM: a codec that cannot be
 * tested is a codec whose failure modes are unknown.
 */
class PendingUpdateTest {

    private val release = CheckResult.UpdateAvailable(
        tag = "v0.6.1",
        versionName = "0.6.1",
        notes = "## Fixed\n- Back from the privacy screen\n- « Mise à jour » en français\n",
    )

    @Test
    fun `a check result is remembered field by field`() {
        val pending = PendingUpdate.of(release)

        assertEquals(release.tag, pending.tag)
        assertEquals(release.versionName, pending.versionName)
        assertEquals(release.notes, pending.notes)
    }

    @Test
    fun `a complete release is usable`() {
        assertTrue(PendingUpdate.of(release).isUsable())
    }

    @Test
    fun `a release without notes is still usable`() {
        // The screen just shows no "What's new" block: missing notes are not a reason to withhold
        // the update from a user who asked for it.
        assertTrue(PendingUpdate.of(release).copy(notes = "").isUsable())
    }

    @Test
    fun `an empty record, the one an untouched install reads back, is not usable`() {
        val nothing = PendingUpdate(tag = "", versionName = "", notes = "")

        assertFalse(nothing.isUsable())
    }

    @Test
    fun `a record missing its tag or its version is refused`() {
        assertFalse(PendingUpdate.of(release).copy(tag = "").isUsable())
        assertFalse(PendingUpdate.of(release).copy(versionName = " ").isUsable())
    }
}
