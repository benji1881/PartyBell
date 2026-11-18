package com.benji.partybell.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.AppSettings
import com.benji.partybell.ChatChannel
import com.benji.partybell.ChatItem
import com.benji.partybell.ChatPrefs
import com.benji.partybell.FeedItem
import com.benji.partybell.Friend
import com.benji.partybell.Notifier
import com.benji.partybell.OutgoingItem
import com.benji.partybell.Party
import com.benji.partybell.PartyState
import com.benji.partybell.Status
import kotlin.math.max

private data class Thread(val conversation: String, val last: FeedItem?, val unread: Int, val sender: ChatItem?)

fun unreadIn(messages: List<ChatItem>, conversation: String, viewed: Map<String, Long>, cleared: Map<String, Long>): Int {
    val after = max(viewed[conversation] ?: 0L, cleared[conversation] ?: 0L)
    return messages.count { it.conversation == conversation && incoming(it, after) }
}

@Composable
fun ChatsScreen(onSettings: () -> Unit, onOpen: (String) -> Unit) {
    val feed by PartyState.feed.collectAsStateWithLifecycle()
    val messages by PartyState.messages.collectAsStateWithLifecycle()
    val outbox by PartyState.outbox.collectAsStateWithLifecycle()
    val viewed by PartyState.viewed.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    val friends by PartyState.friends.collectAsStateWithLifecycle()
    val company by PartyState.company.collectAsStateWithLifecycle()
    val party by PartyState.party.collectAsStateWithLifecycle()
    val pinned by ChatPrefs.pinned.collectAsStateWithLifecycle()
    val muted by ChatPrefs.muted.collectAsStateWithLifecycle()
    val cleared by ChatPrefs.cleared.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<String?>(null) }

    val partySlots = remember(party) { slotsOf(party) }
    val groups = remember(feed, messages, outbox, viewed, cleared, company) {
        val partyChat = feed.filterIsInstance<ChatItem>()
        val party = Thread(
            ChatChannel.PARTY,
            (partyChat + outbox.filter { ChatChannel.isParty(it.channel) }).maxByOrNull { it.time },
            partyChat.count { incoming(it, viewed[it.channel] ?: 0L) },
            null,
        )
        listOfNotNull(party, if (company) thread(ChatChannel.FC, messages, outbox, viewed, cleared) else null)
    }
    val tells = remember(messages, outbox, viewed, cleared, pinned) {
        (messages.map { it.conversation } + outbox.map { it.conversation })
            .filter { ChatChannel.channelOf(it) == ChatChannel.TELL }
            .distinct()
            .map { thread(it, messages, outbox, viewed, cleared) }
            .filter { it.last != null }
            .sortedWith(compareByDescending<Thread> { it.conversation in pinned }.thenByDescending { it.last?.time ?: 0L })
    }

    Column(Modifier.fillMaxSize()) {
        Header("Chats", status, onSettings)
        LazyColumn(Modifier.weight(1f)) {
            item { Section("Groups") }
            items(groups, key = { it.conversation }) { thread ->
                val isParty = thread.conversation == ChatChannel.PARTY
                ThreadRow(
                    thread,
                    friend = null,
                    pinned = !isParty && thread.conversation in pinned,
                    muted = thread.conversation in muted,
                    onOpen = { onOpen(thread.conversation) },
                    onDelete = if (isParty) null else ({ deleting = thread.conversation }),
                    slots = if (isParty) partySlots else null,
                )
            }
            item { Section("Tells") }
            if (tells.isEmpty()) {
                item { Empty("Tells show up here") }
            }
            items(tells, key = { it.conversation }) { thread ->
                ThreadRow(
                    thread,
                    friend = friends.list.firstOrNull { it.key.equals(ChatChannel.peerOf(thread.conversation), ignoreCase = true) },
                    pinned = thread.conversation in pinned,
                    muted = thread.conversation in muted,
                    onOpen = { onOpen(thread.conversation) },
                    onDelete = { deleting = thread.conversation },
                )
            }
        }
    }

    deleting?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete conversation?") },
            text = { Text("${ChatChannel.title(conversation)} is cleared on this phone only.") },
            confirmButton = {
                TextButton(onClick = {
                    val latest = (messages.filter { it.conversation == conversation } + outbox.filter { it.conversation == conversation })
                        .maxOfOrNull { it.time } ?: 0L
                    ChatPrefs.clear(conversation, latest)
                    deleting = null
                }) { Text("Delete", color = AppColors.red) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            containerColor = AppColors.card,
            titleContentColor = AppColors.text,
            textContentColor = AppColors.muted,
        )
    }
}

private fun slotsOf(party: Party): List<Int?>? {
    if (party.members.size <= 1) return null
    val own = party.members.groupBy { it.group }.values.first().map { it.role }.sortedBy { roleRank(it) }
    val size = minOf(party.capacity, Party.GROUP_SIZE)
    return own.take(size) + List(size - own.size.coerceAtMost(size)) { null }
}

private fun thread(
    conversation: String,
    messages: List<ChatItem>,
    outbox: List<OutgoingItem>,
    viewed: Map<String, Long>,
    cleared: Map<String, Long>,
): Thread {
    val after = cleared[conversation] ?: 0L
    val chat = messages.filter { it.conversation == conversation && it.time > after }
    val last = (chat + outbox.filter { it.conversation == conversation }).maxByOrNull { it.time }
    return Thread(conversation, last, unreadIn(messages, conversation, viewed, cleared), chat.lastOrNull { !it.self })
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        color = AppColors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ThreadRow(
    thread: Thread,
    friend: Friend?,
    pinned: Boolean,
    muted: Boolean,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    slots: List<Int?>? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val conversation = thread.conversation
    val peer = ChatChannel.peerOf(conversation)

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(AppColors.card, RoundedCornerShape(18.dp))
                .combinedClickable(onClick = onOpen, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                })
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            when (conversation) {
                ChatChannel.PARTY -> GroupTile(Icons.Filled.Face)
                ChatChannel.FC -> GroupTile(Icons.Filled.Home)
                else -> Box {
                    val sender = thread.sender
                    JobTile(sender?.job ?: friend?.job.orEmpty(), sender?.role ?: friend?.role ?: 0, ChatChannel.nameOf(peer), 44.dp)
                    if (friend != null) {
                        Dot(
                            if (friend.online) AppColors.accent else AppColors.muted,
                            Modifier
                                .align(Alignment.BottomEnd)
                                .background(AppColors.card, RoundedCornerShape(50))
                                .padding(2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ChatChannel.title(conversation),
                        color = AppColors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val world = ChatChannel.worldOf(peer)
                    if (world.isNotEmpty()) {
                        Text(" · $world", color = AppColors.muted, fontSize = 12.sp, maxLines = 1)
                    }
                    if (pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Star, contentDescription = "Pinned", tint = AppColors.yellow, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    thread.last?.let {
                        Text(stamp(context, it.time), color = AppColors.muted, fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preview(thread.last, conversation),
                        color = if (thread.unread > 0) AppColors.text else AppColors.muted,
                        fontSize = 13.sp,
                        fontWeight = if (thread.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (muted) {
                        Text("Muted", color = AppColors.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                    if (thread.unread > 0) {
                        Spacer(Modifier.width(6.dp))
                        Badge(thread.unread)
                    }
                }
                if (slots != null) {
                    SlotBars(slots, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = AppColors.card) {
            if (conversation != ChatChannel.PARTY) {
                DropdownMenuItem(
                    text = { Text(if (pinned) "Unpin" else "Pin to top", color = AppColors.text) },
                    onClick = {
                        ChatPrefs.togglePin(conversation)
                        menu = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (muted) "Unmute notifications" else "Mute notifications", color = AppColors.text) },
                onClick = {
                    ChatPrefs.toggleMute(conversation)
                    menu = false
                },
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete conversation", color = AppColors.red) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupTile(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(AppColors.accent, RoundedCornerShape(13.dp)),
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.onAccent, modifier = Modifier.size(24.dp))
    }
}

private fun preview(item: FeedItem?, conversation: String): String = when (item) {
    null -> if (conversation == ChatChannel.FC) "Free Company chat shows up here" else "No messages yet"
    is ChatItem -> when {
        item.self -> "You: ${item.text}"
        ChatChannel.channelOf(conversation) == ChatChannel.TELL -> item.text
        else -> "${item.name}: ${item.text}"
    }
    is OutgoingItem -> "You: ${item.text}"
    else -> ""
}

private fun stamp(context: Context, time: Long): String =
    if (DateUtils.isToday(time)) {
        DateFormat.getTimeFormat(context).format(time)
    } else {
        DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
    }

@Composable
fun ConversationScreen(conversation: String, onBack: () -> Unit) {
    val messages by PartyState.messages.collectAsStateWithLifecycle()
    val outbox by PartyState.outbox.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    val friends by PartyState.friends.collectAsStateWithLifecycle()
    val cleared by ChatPrefs.cleared.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val channel = ChatChannel.channelOf(conversation)
    val peer = ChatChannel.peerOf(conversation)
    val name = ChatChannel.title(conversation)
    val after = cleared[conversation] ?: 0L
    val items: List<FeedItem> = remember(messages, outbox, after) {
        messages.filter { it.conversation == conversation && it.time > after } + outbox.filter { it.conversation == conversation }
    }
    val friend = friends.list.firstOrNull { it.key.equals(peer, ignoreCase = true) }

    var opened by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(Unit) {
        opened++
        onStopOrDispose { }
    }
    val newAfter = remember(opened) {
        val seen = max(PartyState.viewedUpTo(conversation), after)
        if (items.any { incoming(it, seen) }) seen else Long.MAX_VALUE
    }
    val latest = items.lastOrNull { it is ChatItem }?.time ?: 0L
    LaunchedEffect(latest, opened) {
        PartyState.markViewed(conversation, latest)
        Notifier(context, AppSettings(context)).opened(conversation)
    }

    Column(Modifier.fillMaxSize()) {
        TitleBar(name, onBack) {
            when {
                friend != null -> {
                    Dot(if (friend.online) AppColors.accent else AppColors.muted)
                    Spacer(Modifier.width(6.dp))
                    Text("${friend.world} · ${friend.status.label}", color = AppColors.muted, fontSize = 13.sp)
                }
                peer.isNotEmpty() -> Text(ChatChannel.worldOf(peer), color = AppColors.muted, fontSize = 13.sp)
                else -> Text("Free Company chat", color = AppColors.muted, fontSize = 13.sp)
            }
        }
        if (items.isEmpty()) {
            Empty("No messages yet", Modifier.weight(1f))
        } else {
            ChatFeed(items, newAfter, onRetry = PartyState::retry, modifier = Modifier.weight(1f), showNames = channel != ChatChannel.TELL)
        }
        Composer(
            if (channel == ChatChannel.TELL) "Tell $name…" else "Message Free Company",
            channel,
            enabled = status == Status.Connected,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            to = peer,
        )
    }
}
