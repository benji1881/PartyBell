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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.PartyState
import com.benji.partybell.QueueStatus
import kotlinx.coroutines.delay

private const val CLOCK_MS = 1_000L
private const val MINUTE_MS = 60_000L
private const val PULSE_MS = 900
private const val MAX_PILLS = 3
private const val SHORT_WAIT = 5
private const val LONG_WAIT = 30
private const val LAST_POSITION = 50
private const val TINT_ALPHA = 0.16f
private const val TABULAR = "tnum"
private val CardShape = RoundedCornerShape(20.dp)
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
    var open by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(CLOCK_MS)
        }
    }
    val ready = status.state.popped
    val tone = if (ready) AppColors.yellow else AppColors.accent
    val elapsed = (if (ready) status.ready else now) - status.entered
    val more = status.duties.size > 1

    Column(
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(AppColors.card)
            .background(Brush.verticalGradient(listOf(if (ready) tone.copy(alpha = TINT_ALPHA) else Color.Transparent, Color.Transparent)))
            .border(1.dp, if (ready) tone else AppColors.line, CardShape)
            .clickable(enabled = more, onClickLabel = if (open) "Hide duties" else "Show duties") { open = !open }
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
                Text("Duty Finder", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(tone)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOf(status.state.label, status.job).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = AppColors.muted,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (ready) "READY" else clock(elapsed),
                color = tone,
                fontWeight = FontWeight.Bold,
                fontSize = if (ready) 15.sp else 22.sp,
                letterSpacing = if (ready) 1.sp else 0.sp,
                style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR),
            )
        }
        HorizontalDivider(color = AppColors.line, modifier = Modifier.padding(vertical = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Duty(status.duties.firstOrNull().orEmpty(), Modifier.weight(1f))
            if (more) {
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(AppColors.background, CircleShape)
                        .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(if (open) "Less" else "+${status.duties.size - 1}", color = AppColors.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppColors.muted,
                        modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                    )
                }
            }
        }
        AnimatedVisibility(open && more, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                status.duties.drop(1).forEach { Duty(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            pills(status, elapsed).forEach { PillChip(it, Modifier.weight(1f)) }
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
    val average = Pill("Avg", waitText(status.wait)).takeIf { status.wait >= 0 }
    if (status.state.popped) return listOfNotNull(Pill("Waited", clock(elapsed)), average)
    val position = when {
        status.position > LAST_POSITION -> Pill("In line", "#$LAST_POSITION+")
        status.position > 0 -> Pill("In line", "#${status.position}")
        status.position == 0 -> Pill("In line", "…")
        else -> null
    }
    val counts = status.counts.map { Pill(roleTitle(it.role), "${it.found}/${it.needed}", roleColor(it.role)) }
    return (listOfNotNull(position) + counts + listOfNotNull(average) + Pill("Elapsed", clock(elapsed))).take(MAX_PILLS)
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
