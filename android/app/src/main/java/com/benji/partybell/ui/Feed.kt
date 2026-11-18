package com.benji.partybell.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benji.partybell.ChatItem
import com.benji.partybell.EventKind
import com.benji.partybell.FeedItem
import com.benji.partybell.OutgoingItem
import com.benji.partybell.PartyEventItem
import com.benji.partybell.SendState
import kotlinx.coroutines.launch
import java.util.Calendar

private const val RUN_GAP_MS = 5 * 60_000L
private const val SELF = "\u0000self"
private val Big = 18.dp
private val Small = 5.dp

private sealed interface Line {
    val key: String
}

private data class DayLine(override val key: String, val time: Long) : Line

private data class NewLine(override val key: String, val count: Int) : Line

private data class EventLine(override val key: String, val item: PartyEventItem) : Line

private data class BubbleLine(
    override val key: String,
    val item: FeedItem,
    val sender: String,
    val first: Boolean = true,
    val last: Boolean = true,
) : Line

@Composable
fun ChatFeed(
    items: List<FeedItem>,
    newAfter: Long,
    onRetry: (OutgoingItem) -> Unit,
    modifier: Modifier = Modifier,
    showNames: Boolean = true,
) {
    val lines = remember(items, newAfter) { lines(items, newAfter).asReversed() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var topKey by remember { mutableStateOf(lines.firstOrNull()?.key) }
    var unseen by remember { mutableIntStateOf(0) }
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 } }

    LaunchedEffect(lines) {
        val previous = topKey
        topKey = lines.firstOrNull()?.key
        if (previous == null || previous == topKey) return@LaunchedEffect
        val inserted = lines.indexOfFirst { it.key == previous }
        val fresh = if (inserted < 0) lines else lines.take(inserted)
        val mine = fresh.any { it is BubbleLine && it.sender == SELF }
        if (inserted < 0 || mine || state.firstVisibleItemIndex <= inserted) {
            state.scrollToItem(0)
        } else {
            unseen += fresh.count { it is BubbleLine || it is EventLine }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { if (it == 0) unseen = 0 }
    }

    Box(modifier) {
        LazyColumn(
            state = state,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(lines, key = { it.key }, contentType = { it::class }) { line ->
                when (line) {
                    is DayLine -> DayDivider(line.time)
                    is NewLine -> NewDivider(line.count)
                    is EventLine -> EventPill(line.item)
                    is BubbleLine -> when (val item = line.item) {
                        is ChatItem -> if (item.self) {
                            Mine(item.text, item.time, SendState.Sent, "", line, null)
                        } else {
                            Theirs(item, line, showNames)
                        }
                        is OutgoingItem -> Mine(item.text, item.time, item.state, item.reason, line) { onRetry(item) }
                        is PartyEventItem -> Unit
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = scrolled,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
        ) {
            JumpButton(unseen) {
                unseen = 0
                scope.launch { state.animateScrollToItem(0) }
            }
        }
    }
}

private fun lines(items: List<FeedItem>, newAfter: Long): List<Line> {
    val result = mutableListOf<Line>()
    val counts = mutableMapOf<String, Int>()
    val fresh = items.count { it is ChatItem && !it.self && it.time > newAfter }
    var newShown = newAfter <= 0 || fresh == 0
    var day = -1L

    fun key(prefix: String, item: Any, time: Long): String {
        val base = "$prefix$time:${item.hashCode()}"
        val n = counts.merge(base, 1, Int::plus)!!
        return "$base:$n"
    }

    for (item in items) {
        val itemDay = dayOf(item.time)
        if (itemDay != day) {
            day = itemDay
            result += DayLine(key("d", itemDay, itemDay), item.time)
        }
        if (!newShown && item is ChatItem && !item.self && item.time > newAfter) {
            newShown = true
            result += NewLine("new", fresh)
        }
        result += when (item) {
            is PartyEventItem -> EventLine(key("e", item, item.time), item)
            is ChatItem -> BubbleLine(key("c", item, item.time), item, if (item.self) SELF else item.name)
            is OutgoingItem -> BubbleLine("o${item.id}", item, SELF)
        }
    }

    for (i in result.indices) {
        val line = result[i] as? BubbleLine ?: continue
        val before = result.getOrNull(i - 1) as? BubbleLine
        val after = result.getOrNull(i + 1) as? BubbleLine
        result[i] = line.copy(first = !joins(before, line), last = !joins(line, after))
    }
    return result
}

private fun joins(a: BubbleLine?, b: BubbleLine?) =
    a != null && b != null && a.sender == b.sender && b.item.time - a.item.time < RUN_GAP_MS

private fun dayOf(time: Long): Long = Calendar.getInstance().run {
    timeInMillis = time
    get(Calendar.YEAR) * 1000L + get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DayDivider(time: Long) {
    val context = LocalContext.current
    val label = when {
        DateUtils.isToday(time) -> "Today"
        DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = AppColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(AppColors.card, RoundedCornerShape(50))
                .border(1.dp, AppColors.line, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NewDivider(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = AppColors.accent.copy(alpha = 0.5f))
        Text(
            "$count new",
            color = AppColors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = AppColors.accent.copy(alpha = 0.5f))
    }
}

@Composable
private fun EventPill(item: PartyEventItem) {
    val tint = when (item.kind) {
        EventKind.Full -> AppColors.yellow
        EventKind.Disbanded -> AppColors.red
        EventKind.ReadyCheckDone -> if (item.count == 0) AppColors.accent else AppColors.red
        EventKind.Queue -> AppColors.yellow
        EventKind.Joined, EventKind.Left, EventKind.ReadyCheck -> null
    }
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(tint?.copy(alpha = 0.22f) ?: AppColors.card, shape)
                .border(1.dp, tint?.copy(alpha = 0.6f) ?: AppColors.line, shape)
                .padding(start = if (item.name.isEmpty()) 12.dp else 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            if (item.name.isNotEmpty()) {
                JobTile(item.job, item.role, item.name, 20.dp)
                Spacer(Modifier.width(7.dp))
            }
            Text(item.text, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(" · ${clock(LocalContext.current, item.time)}", color = AppColors.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Theirs(item: ChatItem, line: BubbleLine, showName: Boolean) {
    val shape = RoundedCornerShape(
        topStart = if (line.first) Big else Small,
        topEnd = Big,
        bottomEnd = Big,
        bottomStart = Small,
    )
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (line.first) 6.dp else 2.dp, end = 48.dp),
    ) {
        Box(Modifier.width(34.dp)) {
            if (line.last) JobTile(item.job, item.role, item.name, 30.dp)
        }
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier
                .copyable(item.text)
                .background(AppColors.card, shape)
                .border(1.dp, AppColors.line, shape)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
        ) {
            if (line.first && showName) {
                Text(item.name, color = roleColor(item.role), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(1.dp))
            }
            Text(item.text, color = AppColors.text, fontSize = 15.sp, lineHeight = 21.sp)
            Text(
                clock(LocalContext.current, item.time),
                color = AppColors.muted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun Mine(text: String, time: Long, state: SendState, reason: String, line: BubbleLine, onRetry: (() -> Unit)?) {
    val failed = state == SendState.Failed
    val shape = RoundedCornerShape(
        topStart = Big,
        topEnd = if (line.first) Big else Small,
        bottomEnd = Small,
        bottomStart = Big,
    )
    val onBubble = if (failed) AppColors.text else AppColors.onAccent
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (line.first) 6.dp else 2.dp, start = 56.dp),
    ) {
        Column(
            Modifier
                .copyable(text)
                .background(if (failed) AppColors.red.copy(alpha = 0.18f) else AppColors.accent, shape)
                .then(if (failed) Modifier.border(1.dp, AppColors.red.copy(alpha = 0.6f), shape) else Modifier)
                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
        ) {
            Text(text, color = onBubble, fontSize = 15.sp, lineHeight = 21.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(clock(LocalContext.current, time), color = onBubble.copy(alpha = 0.75f), fontSize = 11.sp)
                when (state) {
                    SendState.Sending -> Clock(11.dp, onBubble.copy(alpha = 0.75f))
                    SendState.Sent -> Icon(Icons.Filled.Check, contentDescription = "Sent", tint = onBubble.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                    SendState.Failed -> Unit
                }
            }
        }
        if (failed) {
            Text(
                buildString {
                    append("Not sent")
                    if (reason.isNotEmpty()) append(" · ").append(reason)
                    if (onRetry != null) append(" · Retry")
                },
                color = AppColors.red,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(top = 3.dp, end = 4.dp)
                    .then(if (onRetry != null) Modifier.clickable(onClick = onRetry) else Modifier),
            )
        }
    }
}

@Composable
private fun JumpButton(count: Int, onClick: () -> Unit) {
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(AppColors.card, CircleShape)
                .border(1.dp, AppColors.line, CircleShape)
                .clickable(onClick = onClick),
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest", tint = AppColors.text)
        }
        if (count > 0) {
            Text(
                if (count > 99) "99+" else count.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .background(AppColors.accent, RoundedCornerShape(50))
                    .widthIn(min = 18.dp)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Modifier.copyable(text: String): Modifier {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    return combinedClickable(onClick = {}, onLongClick = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        copy(context, text)
    })
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun clock(context: Context, time: Long): String = DateFormat.getTimeFormat(context).format(time)
