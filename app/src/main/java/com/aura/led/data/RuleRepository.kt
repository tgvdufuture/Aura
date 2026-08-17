package com.aura.led.data

import kotlinx.coroutines.flow.Flow

class RuleRepository(private val dao: RuleDao) {
    val appRules: Flow<List<AppRule>> = dao.observeAppRules()
    val senderRules: Flow<List<SenderRule>> = dao.observeSenderRules()

    suspend fun getAppRule(pkg: String): AppRule? = dao.getAppRule(pkg)
    suspend fun getAppRules(): List<AppRule> = dao.getAppRules()
    suspend fun upsertAppRule(rule: AppRule) = dao.upsertAppRule(rule)
    suspend fun deleteAppRule(pkg: String) = dao.deleteAppRule(pkg)

    suspend fun findSenderRule(pkg: String, kind: String, matchKey: String): SenderRule? =
        dao.findSenderRule(pkg, kind, matchKey)

    suspend fun findSenderRules(pkg: String, kind: String): List<SenderRule> =
        dao.findSenderRules(pkg, kind)

    suspend fun upsertSenderRule(rule: SenderRule) = dao.upsertSenderRule(rule)
    suspend fun deleteSenderRule(id: Long) = dao.deleteSenderRule(id)

    suspend fun getSetting(key: String, default: String): String = dao.getSetting(key) ?: default
    suspend fun setSetting(key: String, value: String) = dao.upsertSetting(Setting(key, value))
    suspend fun getIntSetting(key: String, default: Int): Int =
        dao.getSetting(key)?.toIntOrNull() ?: default

    suspend fun getBoolSetting(key: String, default: Boolean): Boolean =
        dao.getSetting(key)?.toBooleanStrictOrNull() ?: default

    suspend fun setBoolSetting(key: String, value: Boolean) =
        dao.upsertSetting(Setting(key, value.toString()))
}
