package com.benji.partybell.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.DutyAction
import com.benji.partybell.PartyState
import com.benji.partybell.QueueState
import com.benji.partybell.QueueStatus

private const val CLOCK_MS = 1_000L
private const val MINUTE_MS = 60_000L
private const val PULSE_MS = 900
private const val MAX_PILLS = 3
private const val SHORT_WAIT = 5
private const val LONG_WAIT = 30
private const val LAST_POSITION = 50
private const val TINT_ALPHA = 0.16f
private val BarShape = RoundedCornerShape(20.dp)
private val Hourglass = ImageVector.Builder("Hourglass", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        PathParser().parsePathString(
            "M6,2v6h0.01L6,8.01 10,12l-4,4 0.01,0.01H6V22h12v-5.99h-0.01L18,16l-4,-4 4,-3.99 -0.01,-0.01H18V2H6zM16,16.5V20H8v-3.5l4,-4 4,4zM12,11.5l-4,-4V4h8v3.5l-4,4z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    )
    .build()

private data class Pill(val label: String, val value: String, val color: Color? = null)

@Composable
fun QueueBar(modifier: Modifier = Modifier) {
    val status by PartyState.queue.collectAsStateWithLifecycle()
    status?.let { QueueCard(it, modifier) }
}

@Composable
private fun QueueCard(status: QueueStatus, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val now = rememberNow(CLOCK_MS)
    val ready = status.state.popped
    val tone = if (ready) AppColors.yellow else AppColors.accent
    val elapsed = (if (ready) status.ready else now) - status.entered
    val others = status.duties.drop(1)
    val open = expanded || ready
    val countdown = ready && status.expires > 0

    Column(
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(BarShape)
            .background(AppColors.card)
            .background(Brush.verticalGradient(listOf(if (ready) tone.copy(alpha = TINT_ALPHA) else Color.Transparent, Color.Transparent)))
            .border(1.dp, if (ready) tone else AppColors.line, BarShape)
            .clickable(enabled = !ready, onClickLabel = if (open) "Hide details" else "Show details") { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(tone.copy(alpha = TINT_ALPHA)),
            ) {
                Icon(if (ready) Icons.Filled.Check else Hourglass, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    status.duties.firstOrNull() ?: "Duty Finder",
                    color = AppColors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(tone)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOf(status.state.label, status.job, if (others.isEmpty()) "" else "+${others.size} more")
                            .filter { it.isNotEmpty() }
                            .joinToString(" · "),
                        color = AppColors.muted,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        countdown -> clock(status.expires - now)
                        ready -> "READY"
                        else -> clock(elapsed)
                    },
                    color = tone,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (ready && !countdown) 15.sp else 22.sp,
                    letterSpacing = if (ready && !countdown) 1.sp else 0.sp,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR),
                )
                if (!ready && status.wait > 0) {
                    Text("avg ${waitText(status.wait)}", color = AppColors.muted, fontSize = 11.sp)
                }
            }
            if (!ready) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppColors.muted,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(if (open) 180f else 0f),
                )
            }
        }
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Details(status, others, elapsed, tone) { leaving = true }
            }
        }
    }

    if (leaving) {
        LeaveDialog(status) { leaving = false }
    }
}

@Composable
private fun Details(status: QueueStatus, others: List<String>, elapsed: Long, tone: Color, onLeave: () -> Unit) {
    val ready = status.state.popped
    val pills = pills(status, elapsed)
    HorizontalDivider(color = AppColors.line, modifier = Modifier.padding(top = 10.dp))
    if (others.isNotEmpty()) {
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            others.forEach { Duty(it) }
        }
    }
    if (pills.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            pills.forEach { PillChip(it, Modifier.weight(1f)) }
        }
    }
    if (!ready && status.wait in 1..LONG_WAIT) {
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(AppColors.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((elapsed.toFloat() / (status.wait.coerceAtLeast(SHORT_WAIT) * MINUTE_MS)).coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(tone),
            )
        }
    }
    if (status.actions && status.state != QueueState.Accepted) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            if (status.state == QueueState.Ready) {
                ActionButton("Withdraw", AppColors.background, AppColors.text, Modifier.weight(1f)) { PartyState.duty(DutyAction.WITHDRAW) }
                ActionButton("Commence", AppColors.accent, AppColors.onAccent, Modifier.weight(1f)) { PartyState.duty(DutyAction.COMMENCE) }
            } else {
                ActionButton("Leave queue", AppColors.background, AppColors.red, Modifier.weight(1f), onLeave)
            }
        }
    }
}

@Composable
private fun LeaveDialog(status: QueueStatus, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave the queue?") },
        text = { Text(status.duties.joinToString(", ")) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                PartyState.duty(DutyAction.LEAVE)
            }) { Text("Leave", color = AppColors.red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = AppColors.card,
        titleContentColor = AppColors.text,
        textContentColor = AppColors.muted,
    )
}

@Composable
private fun ActionButton(label: String, background: Color, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}

@Composable
private fun Duty(name: String, modifier: Modifier = Modifier) {
    Text(name, color = AppColors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
private fun PillChip(pill: Pill, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(AppColors.background, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(pill.label, color = AppColors.muted, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(pill.value, color = pill.color ?: AppColors.text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR))
    }
}

@Composable
private fun PulseDot(color: Color) {
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.size(6.dp).graphicsLayer { this.alpha = alpha }.background(color, CircleShape))
}

private fun pills(status: QueueStatus, elapsed: Long): List<Pill> {
    if (status.state.popped) return listOfNotNull(Pill("Waited", clock(elapsed)), Pill("Avg", waitText(status.wait)).takeIf { status.wait > 0 })
    val position = when {
        status.position > LAST_POSITION -> Pill("In line", "#$LAST_POSITION+")
        status.position > 0 -> Pill("In line", "#${status.position}")
        status.position == 0 -> Pill("In line", "…")
        else -> null
    }
    val counts = status.counts.map { Pill(roleTitle(it.role), "${it.found}/${it.needed}", roleColor(it.role)) }
    return (listOfNotNull(position) + counts).take(MAX_PILLS)
}

private fun waitText(minutes: Int) = when (minutes) {
    in 1 until SHORT_WAIT -> "<${SHORT_WAIT}m"
    in SHORT_WAIT..LONG_WAIT -> "${minutes}m"
    else -> ">${LONG_WAIT}m"
}

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
