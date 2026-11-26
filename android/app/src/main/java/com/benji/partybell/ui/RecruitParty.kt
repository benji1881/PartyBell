package com.benji.partybell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benji.partybell.RecruitOptions

// The kinds of Party Finder slot a listing can ask for, as the plugin reads them.
internal enum class RecruitSlot(val code: Int, val role: Int, val short: String, val title: String) {
    ANY(RecruitOptions.ANY, 0, "Any", "Any job"),
    TANK(1, ROLE_TANK, "Tank", "Tank"),
    HEALER(4, ROLE_HEALER, "Healer", "Any healer"),
    REGEN(5, ROLE_HEALER, "Regen", "Regen healer"),
    SHIELD(6, ROLE_HEALER, "Shield", "Shield healer"),
    DPS(2, ROLE_MELEE, "DPS", "Any DPS"),
    MELEE(7, ROLE_MELEE, "Melee", "Melee DPS"),
    RANGED(8, ROLE_MELEE, "Ranged", "Physical ranged DPS"),
    CASTER(9, ROLE_MELEE, "Caster", "Magical ranged DPS"),
    ;

    companion object {
        private val fullParty = listOf(TANK, TANK, REGEN, SHIELD, MELEE, MELEE, RANGED, CASTER)
        private val allianceParty = listOf(TANK, HEALER, HEALER, DPS, DPS, DPS, DPS, DPS)
        private val lightParty = listOf(TANK, HEALER, DPS, DPS)
        private val team = listOf(TANK, HEALER, DPS, DPS, DPS)

        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: ANY

        fun recommended(mode: Int, groups: List<Int>) = groups.flatMap { size ->
            when {
                size == team.size -> team
                size == lightParty.size -> lightParty
                mode == RecruitOptions.ALLIANCE -> allianceParty
                else -> fullParty
            }
        }.map { it.code }
    }
}

@Composable
internal fun PartySlots(slots: List<Int>, mode: Int, groups: List<Int>, noSpectators: Boolean, onChange: (List<Int>) -> Unit) {
    val recommended = RecruitSlot.recommended(mode, groups)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Party", color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Chip("Recommended", slots == recommended) { onChange(recommended) }
        Chip("Clear", slots.all { it == RecruitOptions.ANY }) { onChange(slots.map { RecruitOptions.ANY }) }
    }
    var start = 0
    groups.forEachIndexed { group, size ->
        val first = start
        start += size
        SlotRow(groupNames[mode]?.getOrNull(group), size) { index, modifier ->
            SlotPicker(first + index, RecruitSlot.of(slots[first + index]), modifier) { picked ->
                onChange(slots.toMutableList().also { it[first + index] = picked.code })
            }
        }
    }
    if (mode == RecruitOptions.CUSTOM_MATCH) {
        SlotRow("Spectators", FULL_ROW, Modifier.alpha(if (noSpectators) REMOVED_ALPHA else 1f)) { _, modifier ->
            SlotFace(RecruitSlot.ANY, modifier.padding(vertical = 4.dp))
        }
    }
    Text("Tap a slot to choose who can join it", color = AppColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SlotRow(name: String?, size: Int, modifier: Modifier = Modifier, slot: @Composable (Int, Modifier) -> Unit) {
    Column(modifier) {
        if (name != null) Text(name, color = AppColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = if (name == null) 12.dp else 4.dp)) {
            repeat(size) { slot(it, Modifier.weight(1f)) }
            repeat(FULL_ROW - size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SlotPicker(index: Int, slot: RecruitSlot, modifier: Modifier, onPick: (RecruitSlot) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SlotFace(
            slot,
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Change slot ${index + 1}") { open = true }
                .padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AppColors.card) {
            RecruitSlot.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title, color = if (option == slot) AppColors.accent else AppColors.text) },
                    leadingIcon = { SlotTile(option, MenuIcon) },
                    onClick = {
                        onPick(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SlotFace(slot: RecruitSlot, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SlotTile(slot, maxWidth.coerceAtMost(PartySlot))
        }
        Text(
            slot.short,
            color = if (slot == RecruitSlot.ANY) AppColors.muted else roleColor(slot.role),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SlotTile(slot: RecruitSlot, size: Dp) {
    if (slot == RecruitSlot.ANY) EmptyTile(size) else RoleTile(slot.role, size)
}

private const val FULL_ROW = 8
private val groupNames = mapOf(
    RecruitOptions.ALLIANCE to listOf("Alliance A", "Alliance B", "Alliance C"),
    RecruitOptions.CUSTOM_MATCH to listOf("Team Astra", "Team Umbra"),
)
private const val REMOVED_ALPHA = 0.35f
private val PartySlot = 56.dp
private val MenuIcon = 22.dp
