package com.aura.led.root

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The root half of issue #5.
 *
 * The launch-time probe runs `su -c id` with a one-second timeout, which is shorter than a person
 * taking the root manager's grant prompt. That timeout was cached as "no root" for the whole process,
 * so a grant completed a second later stayed invisible — and the LED test then answered a user who
 * had just enabled root as if they had not. The second case is the same bug from the button's side.
 *
 * Both are read from the source: `su` and a root manager cannot run in a JVM unit test.
 */
class RootProbeTest {

    private val manager = source("app/src/main/java/com/aura/led/root/RootManager.kt").readText()
    private val ui = source("app/src/main/java/com/aura/led/ui/Ui.kt").readText()

    @Test
    fun `a probe that never answered is not remembered as no root`() {
        val cacheLines = manager.lines().filter { it.contains("checked = true") }

        assertEquals("the probe answer is cached in more than one place", 1, cacheLines.size)
        assertTrue(
            "the probe is remembered unconditionally, including the timeout a grant prompt causes",
            cacheLines.single().contains("if (result != null)"),
        )
    }

    @Test
    fun `a lazy check does not spawn su on every command`() {
        assertTrue(
            "a phone without root would run `su` on every LED command",
            manager.contains("ageMs > UNKNOWN_RETRY_MS"),
        )
    }

    @Test
    fun `the LED test re-probes root before blaming the setup`() {
        val testLed = ui.substringAfter("fun testLed()").substringBefore("// Latest requested preview")

        val reprobe = testLed.indexOf("RootManager.refresh()")
        val privilege = testLed.indexOf("val privileged =")
        assertTrue("the test does not re-probe root", reprobe >= 0)
        assertTrue(
            "the probe has to happen before the privilege is read, or the button keeps the stale answer",
            reprobe < privilege,
        )
        assertTrue(
            "the re-probe is unconditional: it would spawn `su` on every tap even with Shizuku granted",
            testLed.contains("if (!ShizukuManager.state.value.hasPermission && !RootManager.state.value.available)"),
        )
    }

    private fun source(path: String): File =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }
            ?: error("$path not found")
}
