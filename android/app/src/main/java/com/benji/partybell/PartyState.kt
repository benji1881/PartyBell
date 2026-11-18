package com.benji.partybell

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object PartyState {
    private const val FEED_SIZE = 200
    private const val MESSAGES_SIZE = 500
    private const val MAX_BYTES = 500
    private val whitespace = Regex("\\s+")

    private val partyFlow = MutableStateFlow(Party())
    private val feedFlow = MutableStateFlow<List<FeedItem>>(emptyList())
    private val messagesFlow = MutableStateFlow<List<ChatItem>>(emptyList())
    private val friendsFlow = MutableStateFlow(Friends())
    private val companyFlow = MutableStateFlow(false)
    private val outboxFlow = MutableStateFlow<List<OutgoingItem>>(emptyList())
    private val statusFlow = MutableStateFlow(Status.Off)
    private val viewedFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val listingsFlow = MutableStateFlow(Listings())
    private val findingFlow = MutableStateFlow(false)
    private val linkFlow = MutableStateFlow(Link.Ok)
    private val noticesFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val invites = mutableMapOf<Long, Friend>()
    private var nextId = 1L

    val party: StateFlow<Party> = partyFlow.asStateFlow()
    val feed: StateFlow<List<FeedItem>> = feedFlow.asStateFlow()
    val messages: StateFlow<List<ChatItem>> = messagesFlow.asStateFlow()
    val friends: StateFlow<Friends> = friendsFlow.asStateFlow()
    val company: StateFlow<Boolean> = companyFlow.asStateFlow()
    val outbox: StateFlow<List<OutgoingItem>> = outboxFlow.asStateFlow()
    val status: StateFlow<Status> = statusFlow.asStateFlow()
    val viewed: StateFlow<Map<String, Long>> = viewedFlow.asStateFlow()
    val listings: StateFlow<Listings> = listingsFlow.asStateFlow()
    val finding: StateFlow<Boolean> = findingFlow.asStateFlow()
    val link: StateFlow<Link> = linkFlow.asStateFlow()
    val notices: SharedFlow<String> = noticesFlow.asSharedFlow()

    var sender: ((json: String) -> Boolean)? = null

    fun clean(text: String): String = text.replace(whitespace, " ").trim()

    fun problem(text: String, channel: String, to: String = ""): String? = when {
        text.startsWith("/") -> "Commands can't be sent from the phone"
        GameText.hasEmoji(text) -> "Emoji can't be sent to the game chat"
        text.encodeToByteArray().size > MAX_BYTES - prefixBytes(channel, to) -> "Too long for the game chat"
        else -> null
    }

    fun apply(incoming: Incoming) {
        when (incoming) {
            is PartyUpdate -> partyFlow.value = incoming.party
            is History -> {
                val (kept, party) = incoming.items.partition { it is ChatItem && it.kept }
                feedFlow.value = party.takeLast(FEED_SIZE)
                messagesFlow.value = merge(if (incoming.all) emptyList() else messagesFlow.value, kept.filterIsInstance<ChatItem>())
                if (incoming.all) {
                    outboxFlow.value = emptyList()
                    viewedFlow.value = emptyMap()
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
                if (incoming.ok) it.copy(state = SendState.Sent) else it.copy(state = SendState.Failed, reason = reason(incoming.reason))
            }
            is FriendsUpdate -> friendsFlow.value = incoming.friends
            is CompanyUpdate -> companyFlow.value = incoming.member
            is Invited -> invites.remove(incoming.id)?.let { friend ->
                noticesFlow.tryEmit(if (incoming.ok) "Invited ${friend.name}" else inviteReason(incoming.reason))
            }
            is ListingsUpdate -> {
                listingsFlow.value = incoming.listings
                findingFlow.value = false
            }
            is Finding -> if (!incoming.ok) {
                findingFlow.value = false
                noticesFlow.tryEmit(findReason(incoming.reason))
            }
            is Opened -> noticesFlow.tryEmit(if (incoming.ok) "Opened in the game" else openReason(incoming.reason))
            is Joining -> noticesFlow.tryEmit(if (incoming.ok) "Join request sent" else joinReason(incoming.reason))
            is IconsUpdate, is Answered, is Dismiss, is PluginVersion, Bye -> Unit
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
            reason = problem ?: if (sent) "" else "Not connected",
            time = System.currentTimeMillis(),
            to = to,
        )
        outboxFlow.value += item
    }

    fun invite(friend: Friend) {
        val id = nextId++
        if (sender?.invoke(Protocol.invite(id, friend)) == true) {
            invites[id] = friend
        } else {
            noticesFlow.tryEmit("Not connected")
        }
    }

    fun find(category: Int) {
        if (sender?.invoke(Protocol.finder(category)) == true) {
            findingFlow.value = true
        } else {
            noticesFlow.tryEmit("Not connected")
        }
    }

    fun open(listing: Listing) {
        if (sender?.invoke(Protocol.open(listing.id)) != true) noticesFlow.tryEmit("Not connected")
    }

    fun join(listing: Listing) {
        if (sender?.invoke(Protocol.join(listing.id)) != true) noticesFlow.tryEmit("Not connected")
    }

    fun answer(ready: Boolean): Boolean = sender?.invoke(Protocol.answer(ready)) == true

    fun retry(item: OutgoingItem) {
        outboxFlow.value = outboxFlow.value.filter { it.id != item.id }
        send(item.channel, item.text, item.to)
    }

    fun forget(conversation: String) {
        outboxFlow.value = outboxFlow.value.filter { it.conversation != conversation }
    }

    fun disconnected() {
        sender = null
        invites.clear()
        findingFlow.value = false
        friendsFlow.value = Friends()
        outboxFlow.value = outboxFlow.value.map {
            if (it.state == SendState.Sending) it.copy(state = SendState.Failed, reason = "Not connected") else it
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
        (current + added).distinct().sortedBy { it.time }.takeLast(MESSAGES_SIZE)

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

    private fun update(id: Long, change: (OutgoingItem) -> OutgoingItem) {
        outboxFlow.value = outboxFlow.value.map { if (it.id == id) change(it) else it }
    }

    private fun reason(code: String) = when (code) {
        "disabled" -> "Writing from phones is off in the plugin"
        "too long" -> "Too long for the game chat"
        "command" -> "Commands can't be sent from the phone"
        "no party" -> "You're not in a party"
        "no alliance" -> "You're not in an alliance"
        "no fc" -> "You're not in a Free Company"
        "not ready" -> "The game isn't ready"
        "invalid" -> "Not a valid name"
        "unsupported" -> "The game can't show some of these characters"
        else -> "The game refused it"
    }

    private fun findReason(code: String) = when (code) {
        "not ready" -> "The game isn't ready"
        "in duty" -> "The Party Finder can't be read in a duty"
        "window open" -> "The Party Finder is open in the game"
        else -> "The game refused the request"
    }

    private fun openReason(code: String) = when (code) {
        "not ready" -> "The game isn't ready"
        "in duty" -> "The Party Finder can't be opened in a duty"
        else -> "The game couldn't open the listing"
    }

    private fun joinReason(code: String) = when (code) {
        "not ready" -> "The game isn't ready"
        "in duty" -> "You can't join a party in a duty"
        "busy" -> "Already joining a party"
        "timeout" -> "The game didn't let you join"
        else -> "The game couldn't join the party"
    }

    private fun inviteReason(code: String) = when (code) {
        "disabled" -> "Party invites from phones are off in the plugin"
        "party full" -> "Your party is full"
        "not leader" -> "Only the party leader can invite"
        "not found" -> "Not on your friend list"
        "offline" -> "They're offline"
        "not ready" -> "The game isn't ready"
        else -> "The game refused the invite"
    }
}
