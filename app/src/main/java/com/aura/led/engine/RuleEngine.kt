package com.aura.led.engine

import com.aura.led.data.RuleRepository
import com.aura.led.data.SenderKind
import com.aura.led.led.LedCommand

/**
 * Resolves which LED command to emit for a notification.
 * Business priority is fixed by the PRD: contact > group > app.
 */
class RuleEngine(private val repository: RuleRepository) {

    suspend fun resolve(
        appPackage: String,
        senderName: String?,
        groupName: String?,
        senderParsingEnabled: Boolean,
    ): LedCommand? {
        val appRule = repository.getAppRule(appPackage) ?: return null
        if (!appRule.enabled) return null

        if (senderParsingEnabled) {
            senderName?.let { name ->
                repository.findSenderRules(appPackage, SenderKind.CONTACT)
                    .firstOrNull { matches(it.matchKey, name) }
                    ?.let { return LedCommand(it.colorHex, it.animationId) }
            }
            groupName?.let { name ->
                repository.findSenderRules(appPackage, SenderKind.GROUP)
                    .firstOrNull { matches(it.matchKey, name) }
                    ?.let { return LedCommand(it.colorHex, it.animationId) }
            }
        }

        return LedCommand(appRule.defaultColorHex, null)
    }

    /**
     * Lenient sender matching: exact, substring (both directions), or phone-number
     * suffix comparison, so "maman" matches "Maman Dupont" and "+33 6.." matches "06..".
     */
    private fun matches(ruleKey: String, senderValue: String): Boolean {
        val a = normalize(ruleKey)
        val b = normalize(senderValue)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b || b.contains(a) || a.contains(b)) return true
        val digitsA = a.filter { it.isDigit() }
        val digitsB = b.filter { it.isDigit() }
        if (digitsA.length >= 6 && digitsB.length >= 6) {
            return digitsA.endsWith(digitsB) || digitsB.endsWith(digitsA)
        }
        return false
    }

    private fun normalize(name: String): String = name.trim().lowercase()
}
