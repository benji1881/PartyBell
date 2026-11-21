package com.benji.partybell.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.benji.partybell.Link
import com.benji.partybell.Notifier
import com.benji.partybell.PartyEventItem
import com.benji.partybell.PartyService
import com.benji.partybell.PartyState
import com.benji.partybell.Status

private enum class Tab(val label: String, val icon: ImageVector) {
    Finder("Finder", Icons.Filled.Search),
    Chats("Chats", Icons.Filled.Email),
    Friends("Friends", Icons.Filled.Person),
    Character("Character", Icons.Filled.AccountCircle),
}

@Composable
fun App(settings: AppSettings, request: String?, onRequestHandled: () -> Unit) {
    var showSettings by rememberSaveable { mutableStateOf(!settings.isPaired) }
    var tab by rememberSaveable { mutableStateOf(Tab.Finder) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(request) {
        val conversation = request ?: return@LaunchedEffect
        showSettings = conversation == Notifier.OPEN_SETTINGS
        if (showSettings) {
            open = null
        } else {
            tab = Tab.Chats
            open = conversation
        }
        onRequestHandled()
    }
    LaunchedEffect(Unit) {
        PartyState.notices.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Surface(color = AppColors.background, modifier = Modifier.fillMaxSize()) {
        when {
            showSettings -> {
                BackHandler { showSettings = false }
                SettingsScreen(settings, onBack = { showSettings = false }, modifier = Modifier.safeDrawingPadding())
            }
            else -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                val onSettings = { showSettings = true }
                val current = open
                QueueBar(Modifier.padding(horizontal = 14.dp))
                LinkBanner(Modifier.padding(horizontal = 14.dp))
                Box(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    when {
                        current != null && ChatChannel.isParty(current) -> {
                            BackHandler { open = null }
                            PartyScreen(current, onBack = { open = null }, onTell = { open = ChatChannel.tell(it) })
                        }
                        current != null -> {
                            BackHandler { open = null }
                            ConversationScreen(current, onBack = { open = null })
                        }
                        tab == Tab.Finder -> FinderScreen(settings, onSettings, onTell = { open = ChatChannel.tell(it) })
                        tab == Tab.Chats -> ChatsScreen(onSettings, onOpen = { open = it })
                        tab == Tab.Friends -> FriendsScreen(onSettings, onMessage = { open = ChatChannel.tell(it.key) })
                        else -> CharacterScreen(settings, onSettings)
                    }
                }
                if (current == null) {
                    if (tab != Tab.Finder) BackHandler { tab = Tab.Finder }
                    BottomNav(tab, onSelect = { tab = it })
                }
            }
        }
    }
}

@Composable
private fun LinkBanner(modifier: Modifier = Modifier) {
    val link by PartyState.link.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (link == Link.Ok) return
    val failed = link == Link.Failed
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (failed) AppColors.card else AppColors.yellow.copy(alpha = 0.2f))
            .border(1.dp, if (failed) AppColors.line else Color.Transparent, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (failed) "Couldn't connect" else "Searching for your PC…",
            color = AppColors.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (failed) {
            Text(
                "Reconnect",
                color = AppColors.onAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.accent)
                    .clickable { PartyService.nudge(context) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    val feed by PartyState.feed.collectAsStateWithLifecycle()
    val messages by PartyState.messages.collectAsStateWithLifecycle()
    val viewed by PartyState.viewed.collectAsStateWithLifecycle()
    val cleared by ChatPrefs.cleared.collectAsStateWithLifecycle()
    val muted by ChatPrefs.muted.collectAsStateWithLifecycle()

    Column(Modifier.background(AppColors.card)) {
        HorizontalDivider(color = AppColors.line)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Tab.entries.forEach { tab ->
                val count = when {
                    tab == selected -> 0
                    tab == Tab.Chats -> messages.map { it.conversation }.distinct()
                        .filter { it !in muted }
                        .sumOf { unreadIn(messages, it, viewed, cleared) } +
                        if (ChatChannel.PARTY in muted) 0 else feed.count { it is ChatItem && incoming(it, viewed[it.channel] ?: 0L) }
                    else -> 0
                }
                val active = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(tab) }
                        .padding(vertical = 6.dp),
                ) {
                    Box {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (active) AppColors.accent else AppColors.muted,
                            modifier = Modifier.size(24.dp),
                        )
                        if (count > 0) {
                            Badge(count, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        color = if (active) AppColors.accent else AppColors.muted,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PartyScreen(conversation: String, onBack: () -> Unit, onTell: (String) -> Unit) {
    val party by PartyState.party.collectAsStateWithLifecycle()
    val feed by PartyState.feed.collectAsStateWithLifecycle()
    val outbox by PartyState.outbox.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    val viewed by PartyState.viewed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val hasAlliance = party.isAlliance || feed.any { it is ChatItem && it.channel == ChatChannel.ALLIANCE }
    var tab by rememberSaveable { mutableStateOf(conversation) }
    val channel = if (hasAlliance) tab else ChatChannel.PARTY
    val items = remember(feed, outbox, channel) { feed.filter { shows(it, channel) } + outbox.filter { it.channel == channel } }

    var opened by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(Unit) {
        opened++
        onStopOrDispose { }
    }
    val newAfter = remember(channel, opened) {
        val seen = PartyState.viewedUpTo(channel)
        if (items.any { incoming(it, seen) }) seen else Long.MAX_VALUE
    }
    val latest = items.lastOrNull { it is ChatItem }?.time ?: 0L
    LaunchedEffect(channel, latest) { PartyState.markViewed(channel, latest) }
    LaunchedEffect(channel, items.lastOrNull(), opened) { Notifier(context, AppSettings(context)).opened(channel) }

    Column(Modifier.fillMaxSize()) {
        TitleBar("Party", onBack) {
            Dot(statusColor(status))
            Spacer(Modifier.width(6.dp))
            Text(status.label, color = AppColors.muted, fontSize = 13.sp)
        }
        PartyStrip(party)
        if (hasAlliance) {
            Spacer(Modifier.height(10.dp))
            Tabs(channel, onSelect = { tab = it }) { other ->
                if (other == channel) 0 else feed.count { it is ChatItem && it.channel == other && incoming(it, viewed[other] ?: 0L) }
            }
        }
        if (items.isEmpty()) {
            Empty("Party chat and changes show up here", Modifier.weight(1f))
        } else {
            ChatFeed(items, newAfter, onRetry = PartyState::retry, modifier = Modifier.weight(1f), onTell = onTell)
        }
        Composer(
            if (channel == ChatChannel.ALLIANCE) "Message alliance" else "Message party",
            channel,
            enabled = status == Status.Connected,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
    }
}

private fun shows(item: FeedItem, channel: String) = when (item) {
    is ChatItem -> item.channel == channel
    else -> item is PartyEventItem
}

fun incoming(item: FeedItem, after: Long) = item is ChatItem && !item.self && item.time > after

@Composable
private fun Tabs(selected: String, onSelect: (String) -> Unit, unread: (String) -> Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(50))
            .border(1.dp, AppColors.line, RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        listOf(ChatChannel.PARTY to "Party", ChatChannel.ALLIANCE to "Alliance").forEach { (channel, label) ->
            val active = channel == selected
            val count = unread(channel)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) AppColors.accent else Color.Transparent)
                    .clickable { onSelect(channel) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = if (active) AppColors.onAccent else AppColors.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(count)
                }
            }
        }
    }
}
