package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benji.partybell.Bis
import com.benji.partybell.BisSet
import com.benji.partybell.BisStat
import com.benji.partybell.Profile
import java.util.Locale

private val BoxShape = RoundedCornerShape(12.dp)
private val StatValue = 44.dp
private val MateriaIcon = 26.dp
private val FoodIcon = 30.dp

@Composable
internal fun StatsCard(set: BisSet) {
    Card {
        CardTitle("Stats")
        set.stats.forEach { StatRow(it) }
    }
}

@Composable
private fun StatRow(stat: BisStat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stat.name, color = AppColors.text, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            "${stat.value}",
            color = AppColors.text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR),
        )
    }
}

@Composable
internal fun SetCard(set: BisSet, modifier: Modifier) {
    Card(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            set.food?.let { food ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).background(AppColors.background, BoxShape).padding(10.dp)) {
                    ItemIcon(food.icon, food.name, FoodIcon)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Food", color = AppColors.muted, fontSize = 11.sp)
                        Text(food.name, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (set.gcd > 0) {
                Column(Modifier.widthIn(min = StatValue).background(AppColors.background, BoxShape).padding(10.dp)) {
                    Text("GCD", color = AppColors.muted, fontSize = 11.sp)
                    Text(String.format(Locale.ROOT, "%.2fs", set.gcd), color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        if (set.materia.isNotEmpty()) {
            CardTitle("Materia to meld")
            set.materia.forEach { materia ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(materia.icon, materia.name, MateriaIcon)
                    Spacer(Modifier.width(10.dp))
                    Text(materia.name, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(" · ${materia.stat} +${materia.value}", color = AppColors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("×${materia.count}", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR))
                }
            }
        }
    }
}

@Composable
internal fun UpgradesCard(bis: Bis, character: Profile?, modifier: Modifier, onLink: () -> Unit) {
    val set = bis.set
    Card(modifier) {
        if (set == null) {
            CardTitle("BiS")
            Text(bis.label(), color = AppColors.muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (character != null) Chip(if (bis.link == null) "Add link" else "Change link", bis.link == null, onClick = onLink)
            return@Card
        }
        UpgradesHeader(set, character)
        UpgradeRows(set, character)
        Spacer(Modifier.weight(1f))
        Line()
        LinkRow(set, bis.site, onLink)
    }
}
