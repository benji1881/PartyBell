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
    const val FC = "fc"
    const val TELL = "tell"
    private const val TELL_PREFIX = "tell:"

    fun conversation(channel: String, peer: String) = if (channel == TELL) TELL_PREFIX + peer else channel

    fun tell(peer: String) = TELL_PREFIX + peer

    fun channelOf(conversation: String) = if (conversation.startsWith(TELL_PREFIX)) TELL else conversation

    fun peerOf(conversation: String) = if (conversation.startsWith(TELL_PREFIX)) conversation.removePrefix(TELL_PREFIX) else ""

    fun isParty(conversation: String) = conversation == PARTY || conversation == ALLIANCE

    fun nameOf(peer: String) = peer.substringBefore('@')

    fun worldOf(peer: String) = peer.substringAfter('@', "")

    fun title(conversation: String) = when (conversation) {
        PARTY -> "Party"
        ALLIANCE -> "Alliance"
        FC -> "Free Company"
        else -> nameOf(peerOf(conversation))
    }
}

enum class Status(val label: String) {
    Off("Offline"),
    Connecting("Connecting…"),
    Connected("Connected"),
    NotPaired("Not paired"),
    UpdatePlugin("Update the plugin"),
    UpdateApp("Update the app"),
}

enum class Link { Ok, Searching, Failed }

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
    val peer: String = "",
) : FeedItem {
    val conversation: String
        get() = ChatChannel.conversation(channel, peer)

    val kept: Boolean
        get() = !ChatChannel.isParty(channel)
}

enum class SendState { Sending, Sent, Failed }

data class OutgoingItem(
    val id: Long,
    val channel: String,
    val text: String,
    val state: SendState,
    val reason: String,
    override val time: Long,
    val to: String = "",
) : FeedItem {
    val conversation: String
        get() = ChatChannel.conversation(channel, to)
}

enum class FriendStatus(val key: String, val label: String) {
    Online("online", "Online"),
    Duty("duty", "In a duty"),
    Away("away", "Away"),
    Busy("busy", "Busy"),
    Offline("offline", "Offline");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Online
    }
}

data class Friend(val name: String, val world: String, val job: String, val role: Int, val status: FriendStatus) {
    val key: String
        get() = "$name@$world"

    val online: Boolean
        get() = status != FriendStatus.Offline
}

data class Friends(val loaded: Boolean = false, val list: List<Friend> = emptyList())

data class Slot(val job: String, val role: Int) {
    val filled: Boolean
        get() = job.isNotEmpty()
}

data class Listing(
    val id: String,
    val duty: String,
    val category: Int,
    val description: String,
    val leader: String,
    val world: String,
    val seconds: Int,
    val ilvl: Int,
    val slots: List<Slot>,
    val fits: Boolean,
    val own: Boolean,
    val locked: Boolean,
    val practice: Boolean,
    val loot: Boolean,
    val completion: Boolean,
    val cleared: Boolean,
    val beginners: Boolean,
    val crossWorld: Boolean,
) {
    val title: String
        get() = duty.ifEmpty { FinderCategory.label(category) }

    val peer: String
        get() = "$leader@$world"

    fun secondsLeft(time: Long, now: Long): Int = (seconds - ((now - time) / 1000).toInt()).coerceAtLeast(0)
}

data class Listings(val category: Int = 0, val time: Long = 0L, val list: List<Listing> = emptyList())

object FinderCategory {
    const val DEFAULT = 2

    val all = listOf(
        1 to "Roulette",
        2 to "Dungeons",
        3 to "Guildhests",
        4 to "Trials",
        5 to "Raids",
        6 to "High-end",
        7 to "PvP",
        8 to "Gold Saucer",
        9 to "FATEs",
        10 to "Treasure hunts",
        11 to "The Hunt",
        12 to "Gathering forays",
        13 to "Deep dungeons",
        14 to "Field operations",
        15 to "V&C dungeons",
        16 to "Other",
    )

    fun label(category: Int) = all.firstOrNull { it.first == category }?.second.orEmpty()
}
