package com.aura.led.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

object SenderKind {
    const val CONTACT = "CONTACT"
    const val GROUP = "GROUP"
}

object SettingsKeys {
    const val LED_TIMEOUT_MS = "ledTimeoutMs"
    const val SCREEN_OFF_ONLY = "screenOffOnly"
    const val SYSTEM_LED_DISABLED = "systemLedDisabled"
}

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val enabled: Boolean = false,
    val defaultColorHex: String = "#0aff10",
    val senderParsingEnabled: Boolean = false,
)

@Entity(tableName = "sender_rules")
data class SenderRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val appPackage: String,
    val matchKey: String,
    val colorHex: String,
    val animationId: String? = null,
)

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM app_rules")
    fun observeAppRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAppRules(): List<AppRule>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    suspend fun getAppRule(pkg: String): AppRule?

    @Upsert
    suspend fun upsertAppRule(rule: AppRule)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun deleteAppRule(pkg: String)

    @Query("SELECT * FROM sender_rules ORDER BY appPackage, kind, matchKey")
    fun observeSenderRules(): Flow<List<SenderRule>>

    @Query("SELECT * FROM sender_rules WHERE appPackage = :pkg AND kind = :kind AND matchKey = :matchKey LIMIT 1")
    suspend fun findSenderRule(pkg: String, kind: String, matchKey: String): SenderRule?

    @Query("SELECT * FROM sender_rules WHERE appPackage = :pkg AND kind = :kind")
    suspend fun findSenderRules(pkg: String, kind: String): List<SenderRule>

    @Upsert
    suspend fun upsertSenderRule(rule: SenderRule)

    @Query("DELETE FROM sender_rules WHERE id = :id")
    suspend fun deleteSenderRule(id: Long)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): String?

    @Upsert
    suspend fun upsertSetting(setting: Setting)
}

@Database(
    entities = [AppRule::class, SenderRule::class, Setting::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aura.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
