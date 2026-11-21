package com.benji.partybell.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.FinderCategory
import com.benji.partybell.Job
import com.benji.partybell.JobIcons

// The Party Finder filter sheets.

private const val PICK_DIM = 0.35f
private val SheetPadding = 20.dp
private val PICK_SIZE = 38.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    category: Int,
    goal: Goal,
    fits: Boolean,
    picks: Set<String>,
    jobs: List<Job>,
    beginners: Boolean,
    blocked: Boolean,
    words: List<String>,
    count: Int,
    onCategory: (Int) -> Unit,
    onGoal: (Goal) -> Unit,
    onJobs: (Boolean, Set<String>) -> Unit,
    onBeginners: (Boolean) -> Unit,
    onBlocked: (Boolean) -> Unit,
    onWords: (List<String>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sprout by JobIcons.sprout.collectAsStateWithLifecycle()
    var jobsSheet by rememberSaveable { mutableStateOf(false) }
    var wordsSheet by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(Modifier.padding(start = SheetPadding, end = SheetPadding, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text, modifier = Modifier.weight(1f))
                Text(
                    "Clear all",
                    color = AppColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            SheetLabel("Category")
            CategoryRow(category, onCategory)
            SheetLabel("Goal")
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.card, RoundedCornerShape(50))
                    .border(1.dp, AppColors.line, RoundedCornerShape(50))
                    .padding(3.dp),
            ) {
                Goal.entries.forEach {
                    val picked = it == goal
                    Text(
                        it.label,
                        color = if (picked) AppColors.onAccent else AppColors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (picked) AppColors.accent else Color.Transparent)
                            .clickable { onGoal(it) }
                            .padding(vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            NavRow("Jobs", jobSummary(fits, picks, jobs).ifEmpty { "Any job" }) { jobsSheet = true }
            Divider()
            ToggleRow(BEGINNERS, beginners, onBeginners, sprout)
            Divider()
            ToggleRow("Show blocked recruiters", blocked, onBlocked)
            Divider()
            NavRow("Highlight words", words.joinToString(", ").ifEmpty { "None" }) { wordsSheet = true }
            Spacer(Modifier.height(12.dp))
            Text(
                if (count == 1) "Show 1 listing" else "Show $count listings",
                color = AppColors.onAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.accent)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
            )
        }
    }
    if (jobsSheet) JobsSheet(fits, picks, jobs, onJobs) { jobsSheet = false }
    if (wordsSheet) WordsSheet(words, onWords) { wordsSheet = false }
}

@Composable
private fun CategoryRow(category: Int, onCategory: (Int) -> Unit) {
    val index = FinderCategory.all.indexOfFirst { it.first == category }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (index - 1).coerceAtLeast(0))
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = SheetPadding),
        modifier = Modifier
            .bleed(SheetPadding)
            .fadeEdges(state.canScrollBackward, state.canScrollForward),
    ) {
        items(FinderCategory.all, key = { it.first }) { (id, label) ->
            Chip(label, id == category) { onCategory(id) }
        }
    }
}

private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(Modifier.padding(start = SheetPadding, end = SheetPadding, bottom = 24.dp)) {
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

@Composable
private fun JobsSheet(fits: Boolean, picks: Set<String>, jobs: List<Job>, onJobs: (Boolean, Set<String>) -> Unit, onDismiss: () -> Unit) {
    SubSheet("Jobs", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Any job", !fits && picks.isEmpty()) { onJobs(false, emptySet()) }
            Chip("My current job", fits) { onJobs(!fits, picks) }
        }
        if (jobs.isEmpty()) {
            Text("Jobs show up here after the plugin connects", color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
        }
        jobs.groupBy { roleName(it.role) }.toList().sortedBy { (_, group) -> roleRank(group.first().role) }.forEach { (role, group) ->
            SheetLabel(roleTitle(role))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { job ->
                    val picked = job.name in picks
                    JobPick(job, picked) { onJobs(fits, if (picked) picks - job.name else picks + job.name) }
                }
            }
        }
    }
}

@Composable
private fun JobPick(job: Job, picked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PICK_SIZE * 0.36f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .border(2.dp, if (picked) AppColors.accent else Color.Transparent, shape)
            .selectable(selected = picked, onClick = onClick)
            .padding(4.dp),
    ) {
        JobTile(job.name, job.role, job.name, PICK_SIZE, Modifier.alpha(if (picked) 1f else PICK_DIM))
    }
}

@Composable
private fun WordsSheet(words: List<String>, onWords: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    SubSheet("Highlight words", onDismiss) {
        if (words.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                words.forEach { word -> Removable(word) { onWords(words - word) } }
            }
            Spacer(Modifier.height(12.dp))
        }
        PillField(
            value = draft,
            placeholder = "Add a word or phrase",
            onChange = { draft = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            onDone = {
                val word = draft.trim()
                if (word.isNotEmpty() && words.none { it.equals(word, ignoreCase = true) }) onWords(words + word)
                draft = ""
            },
        )
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        color = AppColors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit, icon: ImageBitmap? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 6.dp),
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = AppColors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        AppSwitch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
internal fun Removable(label: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.accent.copy(alpha = 0.14f))
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
    ) {
        Text(label, color = AppColors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = AppColors.accent, modifier = Modifier.size(14.dp))
    }
}
