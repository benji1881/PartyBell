package com.benji.partybell

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

object TellHistory {
    private const val SAVE_DELAY_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var file: AtomicFile? = null

    fun load(context: Context) {
        if (file != null) return
        val loaded = AtomicFile(File(context.applicationContext.filesDir, "tells.json"))
        file = loaded
        runCatching { Protocol.savedTells(loaded.readFully().decodeToString()) }.getOrNull()?.let(PartyState::restore)
        scope.launch {
            combine(PartyState.messages, PartyState.viewed) { messages, viewed ->
                SavedTells(
                    messages.filter { it.channel == ChatChannel.TELL },
                    viewed.filterKeys { ChatChannel.channelOf(it) == ChatChannel.TELL },
                )
            }.distinctUntilChanged().drop(1).collectLatest { saved ->
                delay(SAVE_DELAY_MS)
                save(loaded, saved)
            }
        }
    }

    private fun save(file: AtomicFile, saved: SavedTells) {
        val out = file.startWrite()
        runCatching { out.write(Protocol.savedTells(saved).encodeToByteArray()) }
            .onSuccess { file.finishWrite(out) }
            .onFailure { file.failWrite(out) }
    }
}
