package com.benji.partybell

import android.content.Context
import androidx.core.content.edit

enum class NotifyKind(val key: String, val label: String, val default: Boolean) {
    Full("notify_full", "Party full", true),
    Joined("notify_joined", "Someone joined", false),
    Left("notify_left", "Someone left", false),
    Disbanded("notify_disbanded", "Party disbanded", false),
    Chat("notify_chat", "Party chat", false),
    Tells("notify_tells", "Tells", true),
    FreeCompany("notify_fc", "Free Company chat", false),
    ReadyCheck("notify_readycheck", "Ready checks", true),
    Queue("notify_queue", "Duty pops", true),
    Disconnected("notify_disconnected", "Lost connection", true),
    Update("notify_update", "App updates", true),
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("partybell", Context.MODE_PRIVATE)

    val hosts: List<String>
        get() = prefs.getString("hosts", "").orEmpty().split(',').filter(String::isNotEmpty)

    val port: Int
        get() = prefs.getInt("port", 4747)

    val phoneId: String
        get() = prefs.getString("phone_id", "").orEmpty()

    val key: String
        get() {
            val stored = prefs.getString("key", "").orEmpty()
            if (!Pairing.isKey(stored)) return Vault.open(stored).orEmpty()
            prefs.edit { putString("key", Vault.seal(stored)) }
            return stored
        }

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var notifiedUpdate: String
        get() = prefs.getString("notified_update", "").orEmpty()
        set(value) = prefs.edit { putString("notified_update", value) }

    var finderCategory: Int
        get() = prefs.getInt("finder_category", FinderCategory.DEFAULT)
        set(value) = prefs.edit { putInt("finder_category", value) }

    var finderFits: Boolean
        get() = prefs.getBoolean("finder_fits", false)
        set(value) = prefs.edit { putBoolean("finder_fits", value) }

    var finderBeginners: Boolean
        get() = prefs.getBoolean("finder_beginners", false)
        set(value) = prefs.edit { putBoolean("finder_beginners", value) }

    var finderBlocked: Boolean
        get() = prefs.getBoolean("finder_blocked", false)
        set(value) = prefs.edit { putBoolean("finder_blocked", value) }

    var finderGoal: Int
        get() = prefs.getInt("finder_goal", 0)
        set(value) = prefs.edit { putInt("finder_goal", value) }

    var finderJobs: Set<String>
        get() = prefs.getStringSet("finder_jobs", emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet("finder_jobs", value) }

    var finderWords: List<String>
        get() = prefs.getString("finder_words", "").orEmpty().split('\n').filter(String::isNotEmpty)
        set(value) = prefs.edit { putString("finder_words", value.joinToString("\n")) }

    val isPaired: Boolean
        get() = hosts.isNotEmpty() && phoneId.isNotEmpty() && key.isNotEmpty()

    fun pair(pairing: Pairing) = prefs.edit {
        putString("hosts", pairing.hosts.joinToString(","))
        putInt("port", pairing.port)
        putString("phone_id", pairing.id)
        putString("key", Vault.seal(pairing.key))
        putBoolean("enabled", true)
    }

    fun notifies(kind: NotifyKind) = prefs.getBoolean(kind.key, kind.default)

    fun setNotifies(kind: NotifyKind, on: Boolean) = prefs.edit { putBoolean(kind.key, on) }
}
