package com.aura.led.engine

import com.aura.led.data.AppRule
import com.aura.led.data.RuleDao
import com.aura.led.data.RuleRepository
import com.aura.led.data.SenderKind
import com.aura.led.data.SenderRule
import com.aura.led.data.Setting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {

    private val enabledApp = AppRule(
        packageName = "com.whatsapp",
        displayName = "WhatsApp",
        enabled = true,
        defaultColorHex = "#111111",
        senderParsingEnabled = true,
    )

    private fun sender(kind: String, matchKey: String, colorHex: String, animationId: String? = null) =
        SenderRule(kind = kind, appPackage = "com.whatsapp", matchKey = matchKey, colorHex = colorHex, animationId = animationId)

    private fun engine(
        app: AppRule? = enabledApp,
        senders: List<SenderRule> = emptyList(),
    ): RuleEngine {
        val dao = FakeDao(
            appRules = app?.let { mapOf(it.packageName to it) } ?: emptyMap(),
            senderRules = senders.groupBy { it.appPackage to it.kind },
        )
        return RuleEngine(RuleRepository(dao))
    }

    @Test
    fun `returns null when no app rule`() = runTest {
        val result = engine(app = null).resolve("com.whatsapp", "Maman", null, true)
        assertNull(result)
    }

    @Test
    fun `returns null when app rule disabled`() = runTest {
        val result = engine(app = enabledApp.copy(enabled = false)).resolve("com.whatsapp", "Maman", null, true)
        assertNull(result)
    }

    @Test
    fun `falls back to app color when sender parsing disabled`() = runTest {
        val app = enabledApp.copy(senderParsingEnabled = false)
        val result = engine(app = app).resolve("com.whatsapp", "Maman", null, true)
        assertEquals("#111111", result?.colorHex)
        assertNull(result?.animationId)
    }

    @Test
    fun `falls back to app color when no sender matches`() = runTest {
        val app = enabledApp
        val senders = listOf(sender(SenderKind.CONTACT, "papa", "#ff0000"))
        val result = engine(app = app, senders = senders).resolve("com.whatsapp", "Maman", null, true)
        assertEquals("#111111", result?.colorHex)
        assertNull(result?.animationId)
    }

    @Test
    fun `matches contact exactly`() = runTest {
        val senders = listOf(sender(SenderKind.CONTACT, "maman", "#ff0000", "breathing"))
        val result = engine(senders = senders).resolve("com.whatsapp", "maman", null, true)
        assertEquals("#ff0000", result?.colorHex)
        assertEquals("breathing", result?.animationId)
    }

    @Test
    fun `matches contact case-insensitively and by substring`() = runTest {
        val senders = listOf(sender(SenderKind.CONTACT, "maman", "#ff0000"))
        val result = engine(senders = senders).resolve("com.whatsapp", "Maman Dupont", null, true)
        assertEquals("#ff0000", result?.colorHex)
    }

    @Test
    fun `matches phone number by digit suffix`() = runTest {
        val senders = listOf(sender(SenderKind.CONTACT, "612345678", "#00ff00"))
        val result = engine(senders = senders).resolve("com.whatsapp", "0-6-1-2-3-4-5-6-7-8", null, true)
        assertEquals("#00ff00", result?.colorHex)
    }

    @Test
    fun `matches group when contact does not match`() = runTest {
        val senders = listOf(
            sender(SenderKind.CONTACT, "inconnu", "#ff0000"),
            sender(SenderKind.GROUP, "Famille", "#0000ff", "rainbow"),
        )
        val result = engine(senders = senders).resolve("com.whatsapp", "Maman", "Famille", true)
        assertEquals("#0000ff", result?.colorHex)
        assertEquals("rainbow", result?.animationId)
    }

    @Test
    fun `contact rule takes priority over group rule`() = runTest {
        val senders = listOf(
            sender(SenderKind.CONTACT, "maman", "#ff0000"),
            sender(SenderKind.GROUP, "Famille", "#0000ff"),
        )
        val result = engine(senders = senders).resolve("com.whatsapp", "Maman", "Famille", true)
        assertEquals("#ff0000", result?.colorHex)
    }

    private class FakeDao(
        private val appRules: Map<String, AppRule>,
        private val senderRules: Map<Pair<String, String>, List<SenderRule>>,
    ) : RuleDao {
        override fun observeAppRules(): Flow<List<AppRule>> = emptyFlow()
        override fun observeSenderRules(): Flow<List<SenderRule>> = emptyFlow()
        override suspend fun getAppRule(pkg: String): AppRule? = appRules[pkg]
        override suspend fun findSenderRules(pkg: String, kind: String): List<SenderRule> =
            senderRules[pkg to kind] ?: emptyList()

        override suspend fun getAppRules(): List<AppRule> = throw UnsupportedOperationException()
        override suspend fun upsertAppRule(rule: AppRule) = throw UnsupportedOperationException()
        override suspend fun deleteAppRule(pkg: String) = throw UnsupportedOperationException()
        override suspend fun findSenderRule(pkg: String, kind: String, matchKey: String): SenderRule? =
            throw UnsupportedOperationException()
        override suspend fun upsertSenderRule(rule: SenderRule) = throw UnsupportedOperationException()
        override suspend fun deleteSenderRule(id: Long) = throw UnsupportedOperationException()
        override suspend fun getSetting(key: String): String? = throw UnsupportedOperationException()
        override suspend fun upsertSetting(setting: Setting) = throw UnsupportedOperationException()
    }
}
