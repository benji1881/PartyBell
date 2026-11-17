package com.benji.partybell.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.AppSettings
import com.benji.partybell.ChatChannel
import com.benji.partybell.ChatItem
import com.benji.partybell.FeedItem
import com.benji.partybell.PartyEventItem
import com.benji.partybell.PartyState
import com.benji.partybell.Status

@Composable
fun App(settings: AppSettings) {
    var showSettings by rememberSaveable { mutableStateOf(!settings.isPaired) }

    Surface(color = AppColors.background, modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(settings, onBack = { showSettings = false }, modifier = Modifier.safeDrawingPadding())
        } else {
            PartyScreen(onSettings = { showSettings = true })
        }
    }
}

@Composable
private fun PartyScreen(onSettings: () -> Unit) {
    val party by PartyState.party.collectAsStateWithLifecycle()
    val feed by PartyState.feed.collectAsStateWithLifecycle()
    val outbox by PartyState.outbox.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    val viewed by PartyState.viewed.collectAsStateWithLifecycle()

    val hasAlliance = party.isAlliance || feed.any { it is ChatItem && it.channel == ChatChannel.ALLIANCE }
    var tab by rememberSaveable { mutableStateOf(ChatChannel.PARTY) }
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

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 14.dp)
    ) {
        Header(status, onSettings)
        PartyStrip(party)
        if (hasAlliance) {
            Spacer(Modifier.height(10.dp))
            Tabs(channel, onSelect = { tab = it }) { other ->
                if (other == channel) 0 else feed.count { it is ChatItem && it.channel == other && incoming(it, viewed[other] ?: 0L) }
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Party chat and changes show up here",
                    color = AppColors.muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            ChatFeed(items, newAfter, onRetry = PartyState::retry, modifier = Modifier.weight(1f))
        }
        Composer(channel, enabled = status == Status.Connected, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
    }
}

private fun shows(item: FeedItem, channel: String) = when (item) {
    is ChatItem -> item.channel == channel
    else -> item is PartyEventItem
}

private fun incoming(item: FeedItem, after: Long) = item is ChatItem && !item.self && item.time > after

@Composable
private fun Header(status: Status, onSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("PartyBell", fontFamily = Fraunces, fontSize = 28.sp, color = AppColors.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(statusColor(status), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(status.label, color = AppColors.muted, fontSize = 13.sp)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AppColors.card)
                .border(1.dp, AppColors.line, CircleShape)
                .clickable(onClick = onSettings),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = AppColors.text, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun statusColor(status: Status): Color = when (status) {
    Status.Connected -> AppColors.sage
    Status.Connecting -> AppColors.yellow
    Status.Off -> AppColors.muted
    Status.NotPaired, Status.UpdatePlugin, Status.UpdateApp -> AppColors.red
}

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
                    .background(if (active) AppColors.sage else Color.Transparent)
                    .clickable { onSelect(channel) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = if (active) AppColors.onSage else AppColors.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (count > 99) "99+" else count.toString(),
                        color = AppColors.onSage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(AppColors.sage, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
