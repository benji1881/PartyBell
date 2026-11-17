package com.benji.partybell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PartyState {
    private const val FEED_SIZE = 200
    private const val MAX_BYTES = 500
    private const val PREFIX_BYTES = 3
    private val whitespace = Regex("\\s+")

    private val partyFlow = MutableStateFlow(Party())
    private val feedFlow = MutableStateFlow<List<FeedItem>>(emptyList())
    private val outboxFlow = MutableStateFlow<List<OutgoingItem>>(emptyList())
    private val statusFlow = MutableStateFlow(Status.Off)
    private val viewedFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var nextId = 1L

    val party: StateFlow<Party> = partyFlow.asStateFlow()
    val feed: StateFlow<List<FeedItem>> = feedFlow.asStateFlow()
    val outbox: StateFlow<List<OutgoingItem>> = outboxFlow.asStateFlow()
    val status: StateFlow<Status> = statusFlow.asStateFlow()
    val viewed: StateFlow<Map<String, Long>> = viewedFlow.asStateFlow()

    var sender: ((id: Long, channel: String, text: String) -> Boolean)? = null

    fun clean(text: String): String = text.replace(whitespace, " ").trim()

    fun problem(text: String): String? = when {
        text.startsWith("/") -> "Commands can't be sent from the phone"
        text.encodeToByteArray().size > MAX_BYTES - PREFIX_BYTES -> "Too long for the game chat"
        else -> null
    }

    fun apply(incoming: Incoming) {
        when (incoming) {
            is PartyUpdate -> partyFlow.value = incoming.party
            is History -> {
                feedFlow.value = incoming.items.takeLast(FEED_SIZE)
                if (incoming.reset) {
                    outboxFlow.value = emptyList()
                    viewedFlow.value = emptyMap()
                }
            }
            is Live -> {
                feedFlow.value = (feedFlow.value + incoming.item).takeLast(FEED_SIZE)
                (incoming.item as? ChatItem)?.takeIf { it.self }?.let(::echoed)
            }
            is Said -> update(incoming.id) {
                if (incoming.ok) it.copy(state = SendState.Sent) else it.copy(state = SendState.Failed, reason = reason(incoming.reason))
            }
            is IconsUpdate -> Unit
        }
    }

    fun send(channel: String, text: String) {
        val message = clean(text)
        if (message.isEmpty()) return
        val id = nextId++
        val problem = problem(message)
        val sent = problem == null && sender?.invoke(id, channel, message) == true
        val item = OutgoingItem(
            id = id,
            channel = channel,
            text = message,
            state = if (sent) SendState.Sending else SendState.Failed,
            reason = problem ?: if (sent) "" else "Not connected",
            time = System.currentTimeMillis(),
        )
        outboxFlow.value += item
    }

    fun retry(item: OutgoingItem) {
        outboxFlow.value = outboxFlow.value.filter { it.id != item.id }
        send(item.channel, item.text)
    }

    fun disconnected() {
        sender = null
        outboxFlow.value = outboxFlow.value.map {
            if (it.state == SendState.Sending) it.copy(state = SendState.Failed, reason = "Not connected") else it
        }
    }

    fun setStatus(status: Status) {
        statusFlow.value = status
    }

    fun viewedUpTo(channel: String): Long = viewedFlow.value[channel] ?: 0L

    fun markViewed(channel: String, time: Long) {
        if (time > viewedUpTo(channel)) viewedFlow.value += channel to time
    }

    private fun echoed(chat: ChatItem) {
        val match = outboxFlow.value.firstOrNull { it.channel == chat.channel && it.text == chat.text && it.state != SendState.Failed }
            ?: return
        outboxFlow.value = outboxFlow.value.filter { it.id != match.id }
    }

    private fun update(id: Long, change: (OutgoingItem) -> OutgoingItem) {
        outboxFlow.value = outboxFlow.value.map { if (it.id == id) change(it) else it }
    }

    private fun reason(code: String) = when (code) {
        "disabled" -> "Writing from phones is off in the plugin"
        "too long" -> "Too long for the game chat"
        "command" -> "Commands can't be sent from the phone"
        "no party" -> "You're not in a party"
        "no alliance" -> "You're not in an alliance"
        "not ready" -> "The game isn't ready"
        else -> "The game refused it"
    }
}
