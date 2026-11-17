package com.benji.partybell.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.benji.partybell.Member
import com.benji.partybell.Party

private val TileGap = 5.dp
private val MaxTile = 32.dp

@Composable
fun PartyStrip(party: Party, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val inParty = party.members.size > 1
    val groups = party.members.groupBy { it.group }.values.toList()
    val own = groups.firstOrNull().orEmpty()
    val slots = minOf(party.capacity, Party.GROUP_SIZE)
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "arrow")

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.line, RoundedCornerShape(18.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = inParty) { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (inParty) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val tile = min(MaxTile, (maxWidth - TileGap * (slots - 1)) / slots)
                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        own.take(slots).forEach { JobTile(it.job, it.role, it.name, tile) }
                        repeat(slots - own.size.coerceAtMost(slots)) { EmptyTile(tile) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(AppColors.background, RoundedCornerShape(50))
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        "${party.members.size}/${party.capacity}",
                        color = AppColors.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (open) "Hide party" else "Show party",
                        tint = AppColors.muted,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(arrow),
                    )
                }
            } else {
                Text("Not in a party", color = AppColors.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
        }

        AnimatedVisibility(visible = open && inParty, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 6.dp)
            ) {
                groups.forEachIndexed { index, members ->
                    HorizontalDivider(color = AppColors.line)
                    if (party.isAlliance) {
                        Text(
                            if (index == 0) "Your party" else "Party ${index + 1}",
                            color = AppColors.muted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                    members.forEach { MemberRow(it) }
                    if (index == 0) repeat(slots - members.size.coerceAtMost(slots)) { OpenSlotRow() }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: Member) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        JobTile(member.job, member.role, member.name, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.name,
                    color = AppColors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (member.leader) {
                    Spacer(Modifier.width(6.dp))
                    Crown(14.dp, AppColors.yellow)
                }
            }
            if (member.world.isNotEmpty()) Text(member.world, color = AppColors.muted, fontSize = 12.sp, maxLines = 1)
        }
        if (member.level > 0) Text("Lv ${member.level}", color = AppColors.muted, fontSize = 13.sp)
    }
}

@Composable
private fun OpenSlotRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        EmptyTile(36.dp)
        Spacer(Modifier.width(12.dp))
        Text("Open slot", color = AppColors.muted, fontSize = 15.sp)
    }
}
