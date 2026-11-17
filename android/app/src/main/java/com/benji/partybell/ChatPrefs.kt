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
    private const val CLEARED = "chat_cleared"

    private var prefs: SharedPreferences? = null
    private val pinnedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val mutedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val clearedFlow = MutableStateFlow<Map<String, Long>>(emptyMap())

    val pinned: StateFlow<Set<String>> = pinnedFlow.asStateFlow()
    val muted: StateFlow<Set<String>> = mutedFlow.asStateFlow()
    val cleared: StateFlow<Map<String, Long>> = clearedFlow.asStateFlow()

    fun load(context: Context) {
        if (prefs != null) return
        val loaded = context.applicationContext.getSharedPreferences("partybell", Context.MODE_PRIVATE)
        prefs = loaded
        pinnedFlow.value = loaded.getStringSet(PINNED, emptySet()).orEmpty().toSet()
        mutedFlow.value = loaded.getStringSet(MUTED, emptySet()).orEmpty().toSet()
        val json = runCatching { JSONObject(loaded.getString(CLEARED, "{}").orEmpty()) }.getOrDefault(JSONObject())
        clearedFlow.value = json.keys().asSequence().associateWith { json.optLong(it) }
    }

    fun togglePin(conversation: String) {
        pinnedFlow.value = toggle(pinnedFlow.value, conversation)
        prefs?.edit { putStringSet(PINNED, pinnedFlow.value) }
    }

    fun toggleMute(conversation: String) {
        mutedFlow.value = toggle(mutedFlow.value, conversation)
        prefs?.edit { putStringSet(MUTED, mutedFlow.value) }
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
