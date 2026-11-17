package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.benji.partybell.Member
import com.benji.partybell.Party

private const val GROUP_SIZE = 8

@Composable
fun PartyPanel(party: Party, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Panel, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Party", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${party.members.size}/${party.capacity}", color = AppColors.Muted)
        }

        if (party.members.isEmpty()) {
            Text("Not in a party", color = AppColors.Muted, modifier = Modifier.padding(top = 6.dp))
            return@Column
        }

        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            party.members.groupBy { it.group }.toSortedMap().forEach { (group, members) ->
                if (party.capacity > GROUP_SIZE) {
                    Text("Party ${'A' + group}", color = AppColors.Muted, style = MaterialTheme.typography.labelLarge)
                }
                members.forEachIndexed { index, member -> MemberRow(index + 1, member) }
            }
        }
    }
}

@Composable
private fun MemberRow(slot: Int, member: Member) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$slot", color = AppColors.Muted, modifier = Modifier.width(18.dp))
        JobAvatar(member.job, member.role, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(member.world, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (member.leader) {
            Text(
                "Leader",
                color = AppColors.Text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(nameColor(member.name), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text("Lv${member.level}", color = AppColors.Muted)
    }
}
