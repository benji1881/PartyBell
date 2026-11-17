package com.benji.partybell

data class Member(
    val name: String,
    val world: String,
    val job: String,
    val role: Int,
    val level: Int,
    val leader: Boolean,
    val group: Int,
)

data class Party(val members: List<Member> = emptyList(), val capacity: Int = 8)

enum class Status(val label: String) {
    Off("Not connected"),
    Connecting("Connecting"),
    Connected("Connected"),
    NotPaired("Not paired"),
}

enum class EventKind(val key: String, val notify: NotifyKind) {
    Joined("joined", NotifyKind.Joined),
    Left("left", NotifyKind.Left),
    Full("full", NotifyKind.Full),
    Disbanded("disbanded", NotifyKind.Disbanded);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

sealed interface FeedItem {
    val time: Long
}

data class PartyEventItem(
    val kind: EventKind,
    val name: String,
    val job: String,
    val role: Int,
    override val time: Long,
) : FeedItem {
    val text: String
        get() = when (kind) {
            EventKind.Joined -> "Joined the party"
            EventKind.Left -> "Left the party"
            EventKind.Full -> "Party full"
            EventKind.Disbanded -> "Party disbanded"
        }
}

data class ChatItem(
    val name: String,
    val job: String,
    val role: Int,
    val text: String,
    val channel: String,
    override val time: Long,
) : FeedItem
