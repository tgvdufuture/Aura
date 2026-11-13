package com.aura.led.root

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class RootState(val available: Boolean = false)

/** Small root-shell bridge used when Shizuku is not available. */
object RootManager {
    private const val TAG = "AuraRoot"
    private const val CHECK_TIMEOUT_MS = 1_000L

    /** How long a probe that never answered stays un-retried (see [refresh]). */
    private const val UNKNOWN_RETRY_MS = 30_000L

    private val _state = MutableStateFlow(RootState())
    val state: StateFlow<RootState> = _state

    /** A probe answered, and that answer is still the current one. */
    @Volatile
    private var checked = false

    /** Elapsed time of the last probe, whether it answered or not. */
    @Volatile
    private var lastCheckMs = 0L

    fun refresh() {
        val result = run("id", CHECK_TIMEOUT_MS).getOrNull()
        val available = result != null &&
            result.exitCode == 0 &&
            Regex("\\buid=0(?:\\(|\\s|$)").containsMatchIn(result.stdout)
        // Only a probe that answered is remembered. `su` waiting for the root manager's prompt, or a
        // command that never started, says nothing about the phone — caching that as "no root" is
        // what kept a grant completed a second later invisible until the next launch.
        if (result != null) checked = true
        lastCheckMs = SystemClock.elapsedRealtime()
        _state.value = RootState(available)
    }

    /** Checks lazily so notification-only use also benefits from root access. */
    fun isAvailable(): Boolean {
        val ageMs = SystemClock.elapsedRealtime() - lastCheckMs
        if (!checked && (lastCheckMs == 0L || ageMs > UNKNOWN_RETRY_MS)) refresh()
        return _state.value.available
    }

    data class CommandResult(val stdout: String, val exitCode: Int)

    /** Runs a fixed internal command through the device's root provider. */
    fun run(command: String, timeoutMs: Long = 3_000L): Result<CommandResult> = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("root command timed out")
        }
        CommandResult(output.get(1, TimeUnit.SECONDS), process.exitValue())
    }.onFailure { Log.w(TAG, "root command failed: $command", it) }
}
