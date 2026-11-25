package com.benji.partybell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benji.partybell.GearPiece
import com.benji.partybell.Profile
import kotlin.math.roundToInt

private const val RINGS_SHARE = 1.4f
private val TabletopRing = 96.dp
private val FocusSlot = 72.dp
private val GroupLabel = 110.dp

@Composable
internal fun CharacterTabletop(
    character: Profile?,
    tabletop: Tabletop,
    picked: List<Long>,
    slot: Int?,
    modifier: Modifier,
    onSlot: (Int) -> Unit,
    onPick: () -> Unit,
) {
    var top by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth().onGloballyPositioned { top = it.positionInWindow().y.roundToInt() }) {
        val start = top
        val upper = if (start == null) Modifier.weight(1f) else Modifier.height(with(density) { (tabletop.top - start).coerceAtLeast(0).toDp() })
        Box(upper.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val piece = character?.piece(slot)
            val info = AllSlots.firstOrNull { it.index == slot }
            if (piece != null && info != null) {
                FocusedPiece(piece, info, onClose = { onSlot(info.index) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileBlock(character, Modifier.weight(1f))
                    GearRings(character, TabletopRing, Modifier.weight(RINGS_SHARE))
                }
            }
        }
        Spacer(Modifier.height(with(density) { (tabletop.bottom - tabletop.top).toDp() }))
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 14.dp),
        ) {
            GearGroup("Weapons", Weapons, character, onSlot)
            GearGroup("Armour", Armour, character, onSlot)
            GearGroup("Accessories", Accessories, character, onSlot)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val repairs = character.repairs()
                if (repairs > 0) RepairNote(repairs, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                val shown = character.shown(picked)
                if (character != null) {
                    Text(
                        if (shown.isEmpty()) "Pick currencies to show" else shown.joinToString("  ·  ") { "${short(it.count)} ${it.name}" },
                        color = AppColors.muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Pick currencies", onClick = onPick).padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusedPiece(piece: GearPiece, info: SlotInfo, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Close", onClick = onClose),
    ) {
        GearSlot(info, piece, true, FocusSlot, AppColors.background)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(info.label, color = AppColors.muted, fontSize = 12.sp)
            Text(piece.name, fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(piece.detail(info), color = AppColors.muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            PieceMeters(piece)
        }
    }
}

@Composable
private fun GearGroup(title: String, slots: List<SlotInfo>, character: Profile?, onSlot: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = AppColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.width(GroupLabel))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            slots.forEach { SlotButton(it, character, AppColors.background, onSlot) }
        }
    }
}
