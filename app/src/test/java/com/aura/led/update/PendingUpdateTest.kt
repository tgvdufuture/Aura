package com.aura.led.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a tap on the update notification rebuilds the update screen from.
 *
 * One process writes the record, another reads it, so the rules that matter here are the ones that
 * keep a short or foreign record away from the installer. The JSON codec that used to carry it was
 * replaced when the unit tests proved `org.json` is stubbed on the JVM: a codec that cannot be
 * tested is a codec whose failure modes are unknown.
 */
class PendingUpdateTest {

    private val release = CheckResult.UpdateAvailable(
        tag = "v0.6.1",
        versionName = "0.6.1",
        apkUrl = "https://github.com/tgvdufuture/Aura/releases/download/v0.6.1/app-release.apk",
        sizeBytes = 8_891_487L,
        notes = "## Fixed\n- Back from the privacy screen\n- « Mise à jour » en français\n",
    )

    @Test
    fun `a check result is remembered field by field`() {
        val pending = PendingUpdate.of(release)

        assertEquals(release.tag, pending.tag)
        assertEquals(release.versionName, pending.versionName)
        assertEquals(release.apkUrl, pending.apkUrl)
        assertEquals(release.sizeBytes, pending.sizeBytes)
        assertEquals(release.notes, pending.notes)
    }

    @Test
    fun `a complete release is usable`() {
        assertTrue(PendingUpdate.of(release).isUsable())
    }

    @Test
    fun `an empty record, the one an untouched install reads back, is not usable`() {
        val nothing = PendingUpdate(tag = "", versionName = "", apkUrl = "", sizeBytes = 0L, notes = "")

        assertFalse(nothing.isUsable())
    }

    @Test
    fun `a record missing its tag or its version is refused`() {
        assertFalse(PendingUpdate.of(release).copy(tag = "").isUsable())
        assertFalse(PendingUpdate.of(release).copy(versionName = " ").isUsable())
    }

    @Test
    fun `a record pointing anywhere but an https release file is refused`() {
        // The update screen downloads whatever this record points at, so a plain-http or edited
        // entry must not be usable even though every other field is fine.
        assertFalse(
            PendingUpdate.of(release).copy(apkUrl = "http://example.invalid/app.apk").isUsable()
        )
        assertFalse(PendingUpdate.of(release).copy(apkUrl = "").isUsable())
    }
}
