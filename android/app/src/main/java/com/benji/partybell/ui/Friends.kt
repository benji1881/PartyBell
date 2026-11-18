package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.Friend
import com.benji.partybell.FriendStatus
import com.benji.partybell.PartyState
import com.benji.partybell.Status
import kotlinx.coroutines.delay

private const val LOAD_MS = 5_000L

@Composable
fun FriendsScreen(onSettings: () -> Unit, onMessage: (Friend) -> Unit) {
    val friends by PartyState.friends.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    val refreshing by PartyState.refreshingFriends.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(status) {
        if (status == Status.Connected && PartyState.friendsStale()) PartyState.refreshFriends(quiet = true)
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(LOAD_MS)
            PartyState.friendsTimedOut()
        }
    }

    val shown = remember(friends, query) {
        friends.list
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.world.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.name }
    }
    val (online, offline) = shown.partition { it.online }

    Column(Modifier.fillMaxSize()) {
        Header("Friends", status, onSettings, onRefresh = { PartyState.refreshFriends() })
        when {
            status != Status.Connected -> Empty("Not connected", Modifier.weight(1f))
            !friends.loaded && refreshing -> Empty("Loading friends…", Modifier.weight(1f))
            !friends.loaded -> Empty("Open your friend list in game once", Modifier.weight(1f))
            else -> {
                SearchField(query, onChange = { query = it })
                LazyColumn(Modifier.weight(1f)) {
                    item { FriendSection("Online · ${online.size}") }
                    items(online, key = { "on:" + it.key }) { FriendRow(it, onMessage) }
                    item { FriendSection("Offline · ${offline.size}") }
                    items(offline, key = { "off:" + it.key }) { FriendRow(it, onMessage) }
                    if (shown.isEmpty() && query.isNotBlank()) {
                        item { Empty("No friends match \"${query.trim()}\"") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, shape)
            .border(1.dp, AppColors.line, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp, fontFamily = Jakarta),
            cursorBrush = SolidColor(AppColors.sage),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) Text("Search friends", color = AppColors.muted, fontSize = 15.sp)
                    field()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "Clear",
                tint = AppColors.muted,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun FriendSection(title: String) {
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
private fun FriendRow(friend: Friend, onMessage: (Friend) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(AppColors.card, RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            .alpha(if (friend.online) 1f else 0.55f),
    ) {
        JobTile(if (friend.online) friend.job else "", friend.role, friend.name, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.name, color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(statusTint(friend.status))
                Spacer(Modifier.width(6.dp))
                Text("${friend.world} · ${friend.status.label}", color = AppColors.muted, fontSize = 13.sp, maxLines = 1)
            }
        }
        if (friend.online) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppColors.sage.copy(alpha = 0.15f))
                    .clickable { onMessage(friend) },
            ) {
                Icon(Icons.Filled.Email, contentDescription = "Message ${friend.name}", tint = AppColors.sage, modifier = Modifier.size(20.dp))
            }
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { menu = true },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = AppColors.muted, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = AppColors.card) {
                    DropdownMenuItem(
                        text = { Text("Message", color = AppColors.text) },
                        onClick = {
                            menu = false
                            onMessage(friend)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Invite to party", color = AppColors.text) },
                        onClick = {
                            menu = false
                            PartyState.invite(friend)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun statusTint(status: FriendStatus): Color = when (status) {
    FriendStatus.Online -> AppColors.sage
    FriendStatus.Duty -> AppColors.yellow
    FriendStatus.Away, FriendStatus.Busy -> AppColors.red
    FriendStatus.Offline -> AppColors.muted
}
