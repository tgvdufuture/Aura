package com.aura.led.root

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

    private val _state = MutableStateFlow(RootState())
    val state: StateFlow<RootState> = _state

    @Volatile
    private var checked = false

    fun refresh() {
        val available = run("id", CHECK_TIMEOUT_MS).getOrNull()?.let { result ->
            result.exitCode == 0 && Regex("\\buid=0(?:\\(|\\s|$)").containsMatchIn(result.stdout)
        } == true
        checked = true
        _state.value = RootState(available)
    }

    /** Checks lazily so notification-only use also benefits from root access. */
    fun isAvailable(): Boolean {
        if (!checked) refresh()
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
