package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.benji.partybell.FinderCategory
import com.benji.partybell.Job

private const val SHOWN_TABS = 7
private val Underline = 2.dp
private val SideWidth = 360.dp

@Composable
internal fun WideFilters(
    category: Int,
    tiers: Set<String>,
    goal: Goal,
    fits: Boolean,
    picks: Set<String>,
    jobs: List<Job>,
    beginners: Boolean,
    blocked: Boolean,
    words: List<String>,
    onCategory: (Int) -> Unit,
    onTiers: (Set<String>) -> Unit,
    onGoal: (Goal) -> Unit,
    onJobs: (Boolean, Set<String>) -> Unit,
    onBeginners: (Boolean) -> Unit,
    onBlocked: (Boolean) -> Unit,
    onWords: (List<String>) -> Unit,
) {
    var jobsSheet by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    CategoryTabs(category, onCategory)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val scroll = rememberScrollState()
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fadeEdges(scroll.canScrollBackward, scroll.canScrollForward)
                .horizontalScroll(scroll),
        ) {
            if (category == FinderCategory.HIGH_END) {
                TierRow(tiers, onTiers, Modifier)
                Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(AppColors.line))
            }
            GoalChip(goal, onGoal)
            Chip(jobSummary(fits, picks, jobs, SHOWN_JOBS).ifEmpty { "Any job" }, fits || picks.isNotEmpty(), trailing = Icons.Filled.ArrowDropDown) { jobsSheet = true }
            Chip(BEGINNERS, beginners, trailing = Icons.Filled.Check.takeIf { beginners }) { onBeginners(!beginners) }
        }
        Spacer(Modifier.width(8.dp))
        Chip("More", blocked || words.isNotEmpty()) { more = true }
    }
    Spacer(Modifier.height(8.dp))
    if (jobsSheet) JobsSheet(fits, picks, jobs, onJobs) { jobsSheet = false }
    if (more) {
        SideSheet("More filters", onDismiss = { more = false }) {
            ToggleRow("Show blocked recruiters", blocked, onBlocked)
            SheetLabel("Highlight words")
            WordsEditor(words, onWords)
        }
    }
}

@Composable
private fun CategoryTabs(category: Int, onCategory: (Int) -> Unit) {
    val shown = FinderCategory.all.take(SHOWN_TABS)
    val rest = FinderCategory.all.drop(SHOWN_TABS)
    val other = rest.firstOrNull { it.first == category }
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            shown.forEach { (id, label) -> UnderlineTab(label, id == category) { onCategory(id) } }
            Box {
                UnderlineTab(other?.second ?: "More", other != null, Icons.Filled.ArrowDropDown) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = AppColors.card) {
                    rest.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = if (id == category) AppColors.accent else AppColors.text) },
                            onClick = {
                                onCategory(id)
                                menu = false
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = AppColors.line)
    }
}

@Composable
private fun UnderlineTab(label: String, active: Boolean, trailing: ImageVector? = null, onClick: () -> Unit) {
    val accent = AppColors.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .drawBehind {
                if (active) drawRect(accent, Offset(0f, size.height - Underline.toPx()), Size(size.width, Underline.toPx()))
            }
            .padding(vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (active) AppColors.text else AppColors.muted,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        if (trailing != null) Icon(trailing, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun GoalChip(goal: Goal, onGoal: (Goal) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Chip(if (goal == Goal.Any) "Any goal" else goal.label, goal != Goal.Any, trailing = Icons.Filled.ArrowDropDown) { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = AppColors.card) {
            Goal.entries.forEach {
                DropdownMenuItem(
                    text = { Text(if (it == Goal.Any) "Any goal" else it.label, color = if (it == goal) AppColors.accent else AppColors.text) },
                    onClick = {
                        onGoal(it)
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SideSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(SideWidth)
                    .background(AppColors.background, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .clickable(interactionSource = null, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text, modifier = Modifier.weight(1f))
                    Text(
                        "Done",
                        color = AppColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}
