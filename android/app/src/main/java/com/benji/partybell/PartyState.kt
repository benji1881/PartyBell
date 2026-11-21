package com.benji.partybell

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object PartyState {
    private const val FEED_SIZE = 200
    private const val CONVERSATION_SIZE = 1000
    private const val MAX_BYTES = 500
    private const val FRIENDS_FRESH_MS = 60_000L
    private val whitespace = Regex("\\s+")

    private val partyFlow = MutableStateFlow(Party())
    private val feedFlow = MutableStateFlow<List<FeedItem>>(emptyList())
    private val messagesFlow = MutableStateFlow<List<ChatItem>>(emptyList())
    private val friendsFlow = MutableStateFlow(Friends())
    private val companyFlow = MutableStateFlow(false)
    private val outboxFlow = MutableStateFlow<List<OutgoingItem>>(emptyList())
    private val statusFlow = MutableStateFlow(Status.Off)
    private val viewedFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val listingsFlow = MutableStateFlow(emptyMap<Int, Listings>())
    private val findingFlow = MutableStateFlow(false)
    private val refreshingFriendsFlow = MutableStateFlow(false)
    private val linkFlow = MutableStateFlow(Link.Ok)
    private val queueFlow = MutableStateFlow<QueueStatus?>(null)
    private val characterFlow = MutableStateFlow<Profile?>(null)
    private val noticesFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val invites = mutableMapOf<Long, Friend>()
    private var nextId = 1L
    private var friendsAskedAt = 0L
    private var quietRefresh = false

    val party: StateFlow<Party> = partyFlow.asStateFlow()
    val feed: StateFlow<List<FeedItem>> = feedFlow.asStateFlow()
    val messages: StateFlow<List<ChatItem>> = messagesFlow.asStateFlow()
    val friends: StateFlow<Friends> = friendsFlow.asStateFlow()
    val company: StateFlow<Boolean> = companyFlow.asStateFlow()
    val outbox: StateFlow<List<OutgoingItem>> = outboxFlow.asStateFlow()
    val status: StateFlow<Status> = statusFlow.asStateFlow()
    val viewed: StateFlow<Map<String, Long>> = viewedFlow.asStateFlow()
    val listings: StateFlow<Map<Int, Listings>> = listingsFlow.asStateFlow()
    val finding: StateFlow<Boolean> = findingFlow.asStateFlow()
    val refreshingFriends: StateFlow<Boolean> = refreshingFriendsFlow.asStateFlow()
    val link: StateFlow<Link> = linkFlow.asStateFlow()
    val queue: StateFlow<QueueStatus?> = queueFlow.asStateFlow()
    val character: StateFlow<Profile?> = characterFlow.asStateFlow()
    val notices: SharedFlow<String> = noticesFlow.asSharedFlow()

    var sender: ((json: String) -> Boolean)? = null

    fun clean(text: String): String = GameText.fromEmoji(text).replace(whitespace, " ").trim()

    fun problem(text: String, channel: String, to: String = ""): String? = when {
        text.startsWith("/") -> Reasons.COMMAND
        GameText.hasEmoji(text) -> "Emoji can't be sent to the game chat"
        text.encodeToByteArray().size > MAX_BYTES - prefixBytes(channel, to) -> Reasons.TOO_LONG
        else -> null
    }

    fun apply(incoming: Incoming) {
        when (incoming) {
            is PartyUpdate -> partyFlow.value = incoming.party
            is History -> {
                val (kept, party) = incoming.items.partition { it is ChatItem && it.kept }
                feedFlow.value = party.takeLast(FEED_SIZE)
                messagesFlow.value = merge(if (incoming.all) messagesFlow.value.filter { ChatChannel.isTell(it.conversation) } else messagesFlow.value, kept.filterIsInstance<ChatItem>())
                if (incoming.all) {
                    outboxFlow.value = emptyList()
                    viewedFlow.value = viewedFlow.value.filterKeys(ChatChannel::isTell)
                } else if (incoming.reset) {
                    outboxFlow.value = outboxFlow.value.filter { !ChatChannel.isParty(it.channel) }
                    viewedFlow.value = viewedFlow.value.filterKeys { !ChatChannel.isParty(it) }
                }
            }
            is Live -> {
                val item = incoming.item
                if (item is ChatItem && item.kept) {
                    messagesFlow.value = merge(messagesFlow.value, listOf(item))
                } else {
                    feedFlow.value = (feedFlow.value + item).takeLast(FEED_SIZE)
                }
                (item as? ChatItem)?.takeIf { it.self }?.let(::echoed)
            }
            is Said -> update(incoming.id) {
                if (incoming.ok) it.copy(state = SendState.Sent) else it.copy(state = SendState.Failed, reason = Reasons.chat(incoming.reason))
            }
            is FriendsUpdate -> {
                friendsFlow.value = incoming.friends
                if (incoming.friends.loaded) refreshingFriendsFlow.value = false
            }
            is CompanyUpdate -> companyFlow.value = incoming.member
            is Invited -> invites.remove(incoming.id)?.let { friend ->
                noticesFlow.tryEmit(if (incoming.ok) "Invited ${friend.name}" else Reasons.invite(incoming.reason))
            }
            is ListingsUpdate -> {
                val update = incoming.listings
                listingsFlow.value = if (update.category == 0) emptyMap() else listingsFlow.value + (update.category to update)
                findingFlow.value = false
            }
            is Finding -> if (!incoming.ok) {
                findingFlow.value = false
                noticesFlow.tryEmit(Reasons.find(incoming.reason))
            }
            is Refreshing -> if (!incoming.ok) {
                refreshingFriendsFlow.value = false
                if (!quietRefresh) noticesFlow.tryEmit(Reasons.friends(incoming.reason))
            } else if (friendsFlow.value.loaded) {
                refreshingFriendsFlow.value = false
            }
            is Opened -> noticesFlow.tryEmit(if (incoming.ok) "Opened in the game" else Reasons.open(incoming.reason))
            is Joining -> noticesFlow.tryEmit(if (incoming.ok) "Join request sent" else Reasons.join(incoming.reason))
            is QueueUpdate -> queueFlow.value = incoming.status
            is CharacterUpdate -> characterFlow.value = incoming.profile
            is DutyReply -> when {
                !incoming.ok -> noticesFlow.tryEmit(Reasons.duty(incoming.reason))
                incoming.action == DutyAction.LEAVE -> noticesFlow.tryEmit("Left the queue")
            }
            Bye -> {
                listingsFlow.value = emptyMap()
                queueFlow.value = null
                characterFlow.value = null
            }
            is IconsUpdate, is ItemIconsUpdate, is Answered, is Dismiss, is PluginVersion -> Unit
        }
    }

    fun send(channel: String, text: String, to: String = "") {
        val message = clean(text)
        if (message.isEmpty()) return
        val id = nextId++
        val problem = problem(message, channel, to)
        val sent = problem == null && sender?.invoke(Protocol.say(id, channel, message, to)) == true
        val item = OutgoingItem(
            id = id,
            channel = channel,
            text = message,
            state = if (sent) SendState.Sending else SendState.Failed,
            reason = problem ?: if (sent) "" else Reasons.NOT_CONNECTED,
            time = System.currentTimeMillis(),
            to = to,
        )
        outboxFlow.value += item
    }

    fun invite(friend: Friend) {
        val id = nextId++
        if (request(Protocol.invite(id, friend))) invites[id] = friend
    }

    fun find(category: Int) {
        if (request(Protocol.finder(category))) findingFlow.value = true
    }

    fun refreshFriends(quiet: Boolean = false) {
        if (sender?.invoke(Protocol.refreshFriends()) == true) {
            friendsAskedAt = System.currentTimeMillis()
            quietRefresh = quiet
            refreshingFriendsFlow.value = true
        } else if (!quiet) {
            noticesFlow.tryEmit(Reasons.NOT_CONNECTED)
        }
    }

    fun friendsStale(): Boolean = System.currentTimeMillis() - friendsAskedAt > FRIENDS_FRESH_MS

    fun friendsTimedOut() {
        refreshingFriendsFlow.value = false
    }

    fun open(listing: Listing) {
        request(Protocol.open(listing.id))
    }

    fun join(listing: Listing) {
        request(Protocol.join(listing.id))
    }

    fun answer(ready: Boolean): Boolean = sender?.invoke(Protocol.answer(ready)) == true

    fun duty(action: String): Boolean = request(Protocol.duty(action))

    fun retry(item: OutgoingItem) {
        outboxFlow.value = outboxFlow.value.filter { it.id != item.id }
        send(item.channel, item.text, item.to)
    }

    fun forget(conversation: String) {
        outboxFlow.value = outboxFlow.value.filter { it.conversation != conversation }
        messagesFlow.value = messagesFlow.value.filter { it.conversation != conversation }
    }

    fun restore(saved: SavedTells) {
        messagesFlow.value = merge(messagesFlow.value, saved.tells)
        viewedFlow.value = saved.viewed + viewedFlow.value
    }

    fun disconnected() {
        sender = null
        invites.clear()
        findingFlow.value = false
        refreshingFriendsFlow.value = false
        queueFlow.value = null
        characterFlow.value = null
        friendsAskedAt = 0L
        friendsFlow.value = Friends()
        outboxFlow.value = outboxFlow.value.map {
            if (it.state == SendState.Sending) it.copy(state = SendState.Failed, reason = Reasons.NOT_CONNECTED) else it
        }
    }

    fun setStatus(status: Status) {
        statusFlow.value = status
    }

    fun setLink(link: Link) {
        linkFlow.value = link
    }

    fun viewedUpTo(conversation: String): Long = viewedFlow.value[conversation] ?: 0L

    fun markViewed(conversation: String, time: Long) {
        if (time > viewedUpTo(conversation)) viewedFlow.value += conversation to time
    }

    private fun merge(current: List<ChatItem>, added: List<ChatItem>) =
        (current + added).distinct().groupBy { it.conversation }.values.flatMap { chat -> chat.sortedBy { it.time }.takeLast(CONVERSATION_SIZE) }.sortedBy { it.time }

    private fun prefixBytes(channel: String, to: String) = when (channel) {
        ChatChannel.TELL -> "/tell  ".length + to.encodeToByteArray().size
        ChatChannel.FC -> "/fc ".length
        else -> "/p ".length
    }

    private fun echoed(chat: ChatItem) {
        val match = outboxFlow.value.firstOrNull {
            it.conversation.equals(chat.conversation, ignoreCase = true) && it.text == chat.text && it.state != SendState.Failed
        } ?: return
        outboxFlow.value = outboxFlow.value.filter { it.id != match.id }
    }

    // Sends a request to the plugin, or tells the user the phone isn't connected.
    private fun request(json: String): Boolean {
        val sent = sender?.invoke(json) == true
        if (!sent) noticesFlow.tryEmit(Reasons.NOT_CONNECTED)
        return sent
    }

    private fun update(id: Long, change: (OutgoingItem) -> OutgoingItem) {
        outboxFlow.value = outboxFlow.value.map { if (it.id == id) change(it) else it }
    }
}
