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

data class Party(val members: List<Member> = emptyList(), val capacity: Int = GROUP_SIZE) {
    companion object {
        const val GROUP_SIZE = 8
    }

    val isAlliance: Boolean
        get() = capacity > GROUP_SIZE
}

object ChatChannel {
    const val PARTY = "party"
    const val ALLIANCE = "alliance"
}

enum class Status(val label: String) {
    Off("Offline"),
    Connecting("Connecting…"),
    Connected("Connected"),
    NotPaired("Not paired"),
    UpdatePlugin("Update the plugin"),
    UpdateApp("Update the app"),
}

enum class EventKind(val key: String, val notify: NotifyKind) {
    Joined("joined", NotifyKind.Joined),
    Left("left", NotifyKind.Left),
    Full("full", NotifyKind.Full),
    Disbanded("disbanded", NotifyKind.Disbanded),
    ReadyCheck("readycheck", NotifyKind.ReadyCheck),
    ReadyCheckDone("readycheckdone", NotifyKind.ReadyCheck),
    Queue("queue", NotifyKind.Queue);

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
    val count: Int = 0,
    val detail: String = "",
    val answerable: Boolean = false,
) : FeedItem {
    val text: String
        get() = when (kind) {
            EventKind.Joined -> "$name joined"
            EventKind.Left -> "$name left"
            EventKind.Full -> "Party full"
            EventKind.Disbanded -> "Party disbanded"
            EventKind.ReadyCheck -> "Ready check"
            EventKind.ReadyCheckDone -> if (count == 0) "Everyone is ready" else "$count not ready"
            EventKind.Queue -> "$detail is ready"
        }
}

data class ChatItem(
    val name: String,
    val job: String,
    val role: Int,
    val text: String,
    val channel: String,
    val self: Boolean,
    override val time: Long,
) : FeedItem

enum class SendState { Sending, Sent, Failed }

data class OutgoingItem(
    val id: Long,
    val channel: String,
    val text: String,
    val state: SendState,
    val reason: String,
    override val time: Long,
) : FeedItem
