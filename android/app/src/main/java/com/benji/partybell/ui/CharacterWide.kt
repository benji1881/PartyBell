package com.benji.partybell.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.JobIcons
import com.benji.partybell.Profile

private const val SIDE = 1f
private const val MIDDLE = 1.15f
private const val RING_TEXT = 0.24f
private const val RED_ALPHA = 0.12f
private val ListSlot = 36.dp
private val CardRing = 84.dp

@Composable
internal fun CharacterColumns(character: Profile?, picked: List<Long>, modifier: Modifier, onSlot: (Int) -> Unit, onPick: () -> Unit) {
    val repairs = character.repairs()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(SIDE)) {
            GearList("Armour", Armour, character, onSlot)
            CurrencyCard(character, picked, onPick)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(MIDDLE)) {
            Card { ProfileBlock(character, Modifier.fillMaxWidth()) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Weapons.forEach { WeaponTile(it, character, onSlot, Modifier.weight(1f)) }
            }
            Card { GearRings(character, CardRing) }
            if (repairs > 0) RepairNote(repairs, Modifier.fillMaxWidth().padding(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(SIDE)) {
            GearList("Accessories", Accessories, character, onSlot)
            LocationCard(character)
        }
    }
}

@Composable
internal fun ProfileBlock(character: Profile?, modifier: Modifier = Modifier) {
    val fade = animated(if (character != null) 1f else 0f, FADE_MS)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        HeroJob(character)
        if (character != null) {
            Spacer(Modifier.height(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(fade)) {
                LevelPill(character)
                Text(character.name, fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(character.home().ifEmpty { null }, character.toNext()).joinToString(" · "),
                    color = AppColors.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun GearRings(character: Profile?, size: Dp, modifier: Modifier = Modifier) {
    val gear = character?.gear.orEmpty()
    val condition = if (gear.isEmpty()) 0 else gear.sumOf { it.condition.coerceIn(0, FULL) } / gear.size
    val bonded = gear.count { it.spiritbond >= FULL }
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = modifier.fillMaxWidth()) {
        StatRing(if (character != null) 1f else 0f, AppColors.accent, character?.itemLevel?.toString().orEmpty(), "Item level", size)
        StatRing(condition / FULL.toFloat(), if (gear.any { it.needsRepair }) AppColors.red else AppColors.accent, "$condition%", "Condition", size)
        StatRing(if (gear.isEmpty()) 0f else bonded / gear.size.toFloat(), AppColors.yellow, "$bonded/${gear.size}", "Spiritbond full", size)
    }
}

@Composable
private fun StatRing(progress: Float, color: Color, value: String, label: String, size: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(animated(progress, RING_MS), color, size) {
            Text(
                value,
                fontFamily = Fraunces,
                fontSize = (size.value * RING_TEXT).sp,
                color = AppColors.text,
                maxLines = 1,
                style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = AppColors.muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun RepairNote(repairs: Int, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(AppColors.red.copy(alpha = RED_ALPHA), CardShape).then(modifier),
    ) {
        Dot(AppColors.red)
        Spacer(Modifier.width(8.dp))
        Text(repairLabel(repairs), color = AppColors.red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun GearList(title: String, slots: List<SlotInfo>, character: Profile?, onSlot: (Int) -> Unit) {
    Card {
        CardTitle(title)
        slots.sortedByDescending { character?.piece(it.index)?.needsRepair == true }.forEach { GearRow(it, character, onSlot) }
    }
}

@Composable
private fun GearRow(info: SlotInfo, character: Profile?, onSlot: (Int) -> Unit) {
    val piece = character?.piece(info.index)
    val fade = animated(if (character != null) 1f else 0f, FADE_MS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = piece != null, role = Role.Button, onClickLabel = info.label) { onSlot(info.index) },
    ) {
        GearSlot(info, piece, character != null, ListSlot, AppColors.card)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).alpha(fade)) {
            Text(
                piece?.name ?: info.label,
                color = if (piece != null) AppColors.text else AppColors.muted,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                piece == null -> Text("None", color = AppColors.muted, fontSize = 12.sp)
                piece.needsRepair -> Text(
                    "ilvl ${piece.level} · ${piece.condition}% condition",
                    color = AppColors.red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ilvl ${piece.level}", color = AppColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Bar(piece.spiritbond, AppColors.yellow, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeaponTile(info: SlotInfo, character: Profile?, onSlot: (Int) -> Unit, modifier: Modifier) {
    val piece = character?.piece(info.index)
    Card(
        modifier
            .clip(CardShape)
            .clickable(enabled = piece != null, role = Role.Button, onClickLabel = info.label) { onSlot(info.index) }
            .alpha(if (character != null && piece == null) EMPTY_ALPHA else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GearSlot(info, piece, character != null, SlotSize, AppColors.card)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.label, color = AppColors.muted, fontSize = 12.sp)
                Text(piece?.name ?: "None", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                piece?.let { Text("ilvl ${it.level}", color = AppColors.muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun CurrencyCard(character: Profile?, picked: List<Long>, onPick: () -> Unit) {
    val icons by JobIcons.currencies.collectAsStateWithLifecycle()
    val shown = character.shown(picked)
    Card(Modifier.clip(CardShape).clickable(enabled = character != null, role = Role.Button, onClickLabel = "Pick currencies", onClick = onPick)) {
        CardTitle("Currencies")
        if (character != null && shown.isEmpty()) Text("Pick currencies to show", color = AppColors.muted, fontSize = 13.sp)
        shown.forEach { currency ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    icons[currency.id.toString()]?.let { Image(it, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(10.dp))
                Text(currency.name, color = AppColors.text, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    short(currency.count),
                    color = AppColors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR),
                )
            }
        }
    }
}
