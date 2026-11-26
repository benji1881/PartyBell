package com.benji.partybell.ui

import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.Bis
import com.benji.partybell.BisChoice
import com.benji.partybell.BisSet
import com.benji.partybell.BisSlot
import com.benji.partybell.GearPiece
import com.benji.partybell.ItemIcons
import com.benji.partybell.PartyState
import com.benji.partybell.Profile
import java.util.Locale

private const val COUNTED_SLOTS = 12
private const val MAIN_HAND = 0
private const val OFF_HAND = 1
private const val ARROWS = 3
private const val ARROW_TOP = 0.2f
private const val ARROW_STEP = 0.22f
private const val ARROW_DEPTH = 0.16f
private const val ARROW_SIDE = 0.18f
private const val ARROW_STROKE = 0.13f
private const val TINT = 0.14f
internal const val UPGRADE = "Upgrade"
private val RowIcon = 36.dp

internal fun BisSet.gain(character: Profile?, slot: BisSlot) = (slot.level - (character?.piece(slot.slot)?.level ?: 0)).coerceAtLeast(0)

internal fun BisSet.upgrades(character: Profile?) = slots.filter { !it.done }.sortedByDescending { gain(character, it) }

internal fun BisSet.levelGain(character: Profile?) =
    slots.filter { !it.done }.sumOf { gain(character, it) * if (it.slot == MAIN_HAND && slot(OFF_HAND) == null) 2 else 1 } / COUNTED_SLOTS

internal fun Bis.pending(slot: Int) = set?.slot(slot)?.takeIf { !it.done }

internal fun BisSet.left() = slots.size - done

private fun pieceCount(left: Int) = if (left == 1) "1 piece" else "$left pieces"

internal fun Bis.label() = when {
    set?.left() == 0 -> "Full BiS"
    set != null -> "${pieceCount(set.left())} from BiS"
    link != null -> "Loading BiS"
    else -> "Set your BiS"
}

@Composable
internal fun Bis.upgradeColor() = if (set?.left() == 0) AppColors.muted else AppColors.accent

@Composable
internal fun UpgradeArrows(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(this.size.width * ARROW_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        repeat(ARROWS) {
            val top = this.size.height * (ARROW_TOP + it * ARROW_STEP)
            val path = Path().apply {
                moveTo(this@Canvas.size.width * ARROW_SIDE, top + this@Canvas.size.height * ARROW_DEPTH)
                lineTo(this@Canvas.size.width / 2, top)
                lineTo(this@Canvas.size.width * (1 - ARROW_SIDE), top + this@Canvas.size.height * ARROW_DEPTH)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

@Composable
internal fun UpgradeButton(bis: Bis, onClick: () -> Unit) {
    val color = bis.upgradeColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = TINT))
            .clickable(role = Role.Button, onClickLabel = UPGRADE, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        UpgradeArrows(color, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(UPGRADE, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
internal fun UpgradeTab(size: Dp, surface: Color, modifier: Modifier) {
    Text(
        "▲",
        color = AppColors.onAccent,
        fontSize = (size.value * 0.16f).sp,
        lineHeight = (size.value * 0.18f).sp,
        modifier = modifier
            .background(surface, CircleShape)
            .padding(1.5.dp)
            .background(AppColors.accent, CircleShape)
            .padding(horizontal = size * 0.1f),
    )
}

@Composable
internal fun ItemIcon(icon: Long, name: String, size: Dp) {
    val items by ItemIcons.icons.collectAsStateWithLifecycle()
    Box(Modifier.size(size).clip(CircleShape).background(AppColors.line), contentAlignment = Alignment.Center) {
        items[icon]?.let { Image(it, contentDescription = name, filterQuality = FilterQuality.High, modifier = Modifier.size(size)) }
    }
}

@Composable
internal fun UpgradesHeader(set: BisSet, character: Profile?) {
    val left = set.left()
    Column {
        Text(if (left == 0) "Full BiS" else "${pieceCount(left)} from BiS", fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text)
        val gain = set.levelGain(character)
        Text(
            listOfNotNull(character?.jobName, if (gain > 0) "Full BiS adds +$gain item level" else null).joinToString(" · "),
            color = AppColors.muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun UpgradeRows(set: BisSet, character: Profile?) {
    set.upgrades(character).forEach { slot ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            ItemIcon(slot.icon, slot.name, RowIcon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(slot.name, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${AllSlots.firstOrNull { it.index == slot.slot }?.label.orEmpty()} · ilvl ${slot.level}", color = AppColors.muted, fontSize = 12.sp)
            }
            Gain(set.gain(character, slot))
        }
    }
}

@Composable
private fun Gain(gain: Int) {
    Text("+$gain", color = AppColors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR))
}

@Composable
internal fun LinkRow(set: BisSet, site: String, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${set.name.ifEmpty { "Gear set" }} · $site", color = AppColors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Chip("Change link", false, onClick = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradesSheet(set: BisSet, site: String, character: Profile?, onChange: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            UpgradesHeader(set, character)
            Spacer(Modifier.height(12.dp))
            UpgradeRows(set, character)
            Line(Modifier.padding(vertical = 14.dp))
            LinkRow(set, site, onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkSheet(jobName: String, link: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(link.orEmpty()) }
    val save = { if (PartyState.bisLink(text)) onDismiss() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Column {
                Text("Best in slot (BiS)", fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text)
                Text("Paste an Etro or xivgear link for $jobName. Each job keeps its own.", color = AppColors.muted, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillField(text, "etro.gg or xivgear.app link", { text = it }, Modifier.weight(1f), onDone = save)
                Spacer(Modifier.width(8.dp))
                Chip("Paste", false) {
                    context.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.let { text = it.getItemAt(0).coerceToText(context).toString().trim() }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                if (link != null) Chip("Remove", false) { if (PartyState.bisLink("")) onDismiss() }
                Chip("Save", true, onClick = save)
            }
        }
    }
}

@Composable
internal fun BisCompare(piece: GearPiece, slot: BisSlot) {
    Line(Modifier.padding(vertical = 16.dp))
    if (slot.done) {
        Text("Already BiS", color = AppColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        return
    }
    Text("BiS piece", color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemIcon(slot.icon, slot.name, RowIcon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(slot.name, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Now ${piece.level} → ${slot.level}", color = AppColors.muted, fontSize = 12.sp)
        }
        Gain((slot.level - piece.level).coerceAtLeast(0))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetPickSheet(choices: List<BisChoice>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Pick a set", fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text)
            Text("This sheet has ${choices.size} sets.", color = AppColors.muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            choices.forEachIndexed { index, choice ->
                if (index > 0) Line()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { if (PartyState.bisLink(choice.link)) onDismiss() }
                        .padding(vertical = 12.dp),
                ) {
                    Text(choice.name.ifEmpty { "Gear set" }, color = AppColors.text, fontSize = 15.sp)
                    val summary = choice.stats.map { "${it.name} ${it.value}" } + listOfNotNull(choice.gcd.takeIf { it > 0 }?.let { "GCD ${gcdText(it)}" })
                    if (summary.isNotEmpty()) Text(summary.joinToString(" · "), color = AppColors.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

internal fun gcdText(gcd: Double): String = String.format(Locale.ROOT, "%.2fs", gcd)
