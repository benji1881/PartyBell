package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.ChatChannel
import com.benji.partybell.ChatItem
import com.benji.partybell.ChatPrefs
import com.benji.partybell.PartyState

internal enum class Tab(val label: String, val icon: ImageVector) {
    Finder("Finder", Icons.Filled.Search),
    Chats("Chats", Icons.Filled.Email),
    Friends("Friends", Icons.Filled.Person),
    Character("Character", Icons.Filled.AccountCircle),
}

private val RailWidth = 80.dp

@Composable
internal fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    val unread = unreadChats()
    Column(Modifier.background(AppColors.card)) {
        HorizontalDivider(color = AppColors.line)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(tab) }
                        .padding(vertical = 6.dp),
                ) {
                    TabIcon(tab, active, if (tab == Tab.Chats && !active) unread else 0)
                    Spacer(Modifier.height(2.dp))
                    TabLabel(tab, active)
                }
            }
        }
    }
}

@Composable
internal fun NavRail(selected: Tab, onSelect: (Tab) -> Unit) {
    val unread = unreadChats()
    Row(Modifier.fillMaxHeight().background(AppColors.card)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(RailWidth).padding(top = 16.dp),
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    TabIcon(
                        tab,
                        active,
                        if (tab == Tab.Chats && !active) unread else 0,
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) AppColors.accent.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    TabLabel(tab, active)
                }
            }
        }
        VerticalDivider(color = AppColors.line)
    }
}

@Composable
private fun TabIcon(tab: Tab, active: Boolean, count: Int, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        BadgedBox(badge = { if (count > 0) Badge(count) }) {
            Icon(tab.icon, contentDescription = null, tint = if (active) AppColors.accent else AppColors.muted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TabLabel(tab: Tab, active: Boolean) {
    Text(
        tab.label,
        color = if (active) AppColors.accent else AppColors.muted,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
    )
}

@Composable
private fun unreadChats(): Int {
    val feed by PartyState.feed.collectAsStateWithLifecycle()
    val messages by PartyState.messages.collectAsStateWithLifecycle()
    val viewed by PartyState.viewed.collectAsStateWithLifecycle()
    val cleared by ChatPrefs.cleared.collectAsStateWithLifecycle()
    val muted by ChatPrefs.muted.collectAsStateWithLifecycle()
    return messages.map { it.conversation }.distinct()
        .filter { it !in muted }
        .sumOf { unreadIn(messages, it, viewed, cleared) } +
        if (ChatChannel.PARTY in muted) 0 else feed.count { it is ChatItem && incoming(it, viewed[it.channel] ?: 0L) }
}
