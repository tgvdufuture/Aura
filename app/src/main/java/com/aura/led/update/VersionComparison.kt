package com.aura.led.update

/**
 * Compares the installed version name with a GitHub release tag.
 *
 * Pure Kotlin so it is unit-testable. Anything that is not a plain `X.Y.Z` (an unparsable
 * tag, a pre-release suffix, an empty string) is treated as "not newer": Aura only offers
 * an update it is sure about.
 */
object VersionComparison {
    private const val MAX_COMPONENTS = 4

    /** `"v0.6.0"`, `"0.6.0"` and `"v0.6"` become `[0, 6, 0, 0]`; anything else becomes null. */
    fun normalize(tag: String): IntArray? {
        val trimmed = tag.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('.')
        if (parts.size > MAX_COMPONENTS) return null
        val numbers = IntArray(MAX_COMPONENTS)
        parts.forEachIndexed { index, part ->
            val number = part.toIntOrNull() ?: return null
            if (number < 0) return null
            numbers[index] = number
        }
        return numbers
    }

    fun isNewer(current: String, candidate: String): Boolean {
        val installed = normalize(current) ?: return false
        val latest = normalize(candidate) ?: return false
        for (index in installed.indices) {
            if (latest[index] != installed[index]) return latest[index] > installed[index]
        }
        return false
    }
}
