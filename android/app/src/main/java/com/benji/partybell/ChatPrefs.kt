package com.benji.partybell

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object ChatPrefs {
    private const val PINNED = "chat_pinned"
    private const val MUTED = "chat_muted"
    private const val MUTED_UNTIL = "chat_muted_until"
    private const val CLEARED = "chat_cleared"

    private var prefs: SharedPreferences? = null
    private val pinnedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val mutedFlow = MutableStateFlow(Mutes())
    private val clearedFlow = MutableStateFlow<Map<String, Long>>(emptyMap())

    val pinned: StateFlow<Set<String>> = pinnedFlow.asStateFlow()
    val muted: StateFlow<Mutes> = mutedFlow.asStateFlow()
    val cleared: StateFlow<Map<String, Long>> = clearedFlow.asStateFlow()

    fun load(context: Context) {
        if (prefs != null) return
        val loaded = context.applicationContext.getSharedPreferences("partybell", Context.MODE_PRIVATE)
        prefs = loaded
        pinnedFlow.value = loaded.getStringSet(PINNED, emptySet()).orEmpty().toSet()
        val ends = json(loaded, MUTED_UNTIL)
        val now = System.currentTimeMillis()
        mutedFlow.value = Mutes(loaded.getStringSet(MUTED, emptySet()).orEmpty().toSet(), ends.filterValues { it > now })
        clearedFlow.value = json(loaded, CLEARED)
    }

    fun togglePin(conversation: String) {
        pinnedFlow.value = toggle(pinnedFlow.value, conversation)
        prefs?.edit { putStringSet(PINNED, pinnedFlow.value) }
    }

    fun mute(conversation: String, until: Long) {
        val current = mutedFlow.value
        mutedFlow.value = if (until == 0L) {
            Mutes(current.always + conversation, current.ends - conversation)
        } else {
            Mutes(current.always - conversation, current.ends + (conversation to until))
        }
        saveMutes()
    }

    fun unmute(conversation: String) {
        val current = mutedFlow.value
        mutedFlow.value = Mutes(current.always - conversation, current.ends - conversation)
        saveMutes()
    }

    private fun saveMutes() {
        val current = mutedFlow.value
        prefs?.edit {
            putStringSet(MUTED, current.always)
            putString(MUTED_UNTIL, JSONObject(current.ends).toString())
        }
    }

    private fun json(prefs: SharedPreferences, key: String): Map<String, Long> {
        val json = runCatching { JSONObject(prefs.getString(key, "{}").orEmpty()) }.getOrDefault(JSONObject())
        return json.keys().asSequence().associateWith { json.optLong(it) }
    }

    fun clear(conversation: String, upTo: Long) {
        clearedFlow.value += conversation to upTo
        pinnedFlow.value -= conversation
        prefs?.edit {
            putString(CLEARED, JSONObject(clearedFlow.value).toString())
            putStringSet(PINNED, pinnedFlow.value)
        }
        PartyState.forget(conversation)
    }

    fun clearedUpTo(conversation: String) = clearedFlow.value[conversation] ?: 0L

    private fun toggle(set: Set<String>, value: String) = if (value in set) set - value else set + value
}

data class Mutes(val always: Set<String> = emptySet(), val ends: Map<String, Long> = emptyMap()) {
    operator fun contains(conversation: String) = conversation in always || endOf(conversation) > 0

    fun endOf(conversation: String) = ends[conversation]?.takeIf { it > System.currentTimeMillis() } ?: 0L
}
