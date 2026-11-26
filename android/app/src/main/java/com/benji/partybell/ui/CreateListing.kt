package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.PartyState
import com.benji.partybell.RecruitDuty
import com.benji.partybell.RecruitForm
import com.benji.partybell.RecruitOptions

private const val SHORT_LIST = 8
private const val SEARCH_LIST = 30
private const val DISABLED_ALPHA = 0.45f
private val RecruitSlot = 46.dp
private val SlotCycle = listOf(RecruitOptions.ANY, ROLE_TANK, ROLE_HEALER, ROLE_MELEE)

@Composable
internal fun CreateListingCard(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(AppColors.card)
            .border(1.dp, AppColors.line, CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(Modifier.size(40.dp).background(AppColors.accent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = AppColors.onAccent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Create listing", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Recruit a party from your phone", color = AppColors.muted, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun CreateListing(onClose: () -> Unit) {
    val recruit by PartyState.recruit.collectAsStateWithLifecycle()
    val posting by PartyState.posting.collectAsStateWithLifecycle()
    var form by remember { mutableStateOf(RecruitForm()) }
    LaunchedEffect(Unit) { PartyState.posted.collect { onClose() } }
    LaunchedEffect(recruit.open) { if (!recruit.open && !posting) onClose() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding().imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                CircleButton(Icons.Filled.Close, "Close", onClose)
                Text("Create listing", fontFamily = Fraunces, fontSize = 24.sp, color = AppColors.text)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Section("Category") {
                    Choices(RecruitOptions.categories, form.category) { form = form.withCategory(it) }
                }
                if (RecruitOptions.needsDuty(form.category)) {
                    Section("Duty") {
                        DutyPicker(recruit.duties.filter { it.category == form.category }, form.duty) { form = form.copy(duty = it) }
                    }
                }
                Section("Party") {
                    PartySlots(form.slots) { form = form.copy(slots = it) }
                }
                Section("Goal") {
                    Choices(RecruitOptions.objectives, form.objective) { form = form.copy(objective = it) }
                }
                Section("Completion") {
                    Choices(RecruitOptions.completions, form.completion) { form = form.copy(completion = it) }
                }
                Section("Loot") {
                    Choices(RecruitOptions.loots, form.loot) { form = form.copy(loot = it) }
                }
                Section("Requirements") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(form.itemLevel.takeIf { it > 0 }, "Min item level", RecruitOptions.MAX_ITEM_LEVEL, Modifier.weight(1f)) {
                            form = form.copy(itemLevel = it ?: 0)
                        }
                        NumberField(form.password, "Password", RecruitOptions.MAX_PASSWORD, Modifier.weight(1f)) {
                            form = form.copy(password = it)
                        }
                    }
                    ToggleRow("Beginners welcome", form.beginners, { form = form.copy(beginners = it) })
                    ToggleRow("One player per job", form.onePerJob, { form = form.copy(onePerJob = it) })
                }
                Section("Comment") {
                    CommentField(form.comment, form.tooLong) { form = form.copy(comment = it) }
                }
            }
            PostButton(form, posting, Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card {
        SheetLabel(title)
        content()
    }
}

@Composable
private fun Choices(options: List<Pair<Int, String>>, picked: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Chip(label, value == picked) { onPick(value) } }
    }
}

@Composable
private fun DutyPicker(duties: List<RecruitDuty>, picked: Long, onPick: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = duties.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val shown = matches.take(if (query.isBlank()) SHORT_LIST else SEARCH_LIST)
    val selected = duties.firstOrNull { it.id == picked }
    PillField(query, "Search duties", { query = it }, Modifier.fillMaxWidth(), Icons.Filled.Search)
    if (selected != null && selected !in shown) DutyRow(selected, true) { onPick(0) }
    shown.forEach { duty -> DutyRow(duty, duty.id == picked) { onPick(if (duty.id == picked) 0 else duty.id) } }
    when {
        duties.isEmpty() -> Text("No duties unlocked in this category", color = AppColors.muted, fontSize = 13.sp)
        matches.isEmpty() -> Text("No duty matches", color = AppColors.muted, fontSize = 13.sp)
        matches.size > shown.size -> Text("Search to see more", color = AppColors.muted, fontSize = 13.sp)
    }
}

@Composable
private fun DutyRow(duty: RecruitDuty, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) AppColors.accent else AppColors.background)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            duty.name,
            color = if (active) AppColors.onAccent else AppColors.text,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.onAccent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PartySlots(slots: List<Int>, onChange: (List<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.forEachIndexed { index, role ->
            val next = SlotCycle[(SlotCycle.indexOf(role) + 1) % SlotCycle.size]
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Change slot ${index + 1}") {
                        onChange(slots.toMutableList().also { it[index] = next })
                    },
            ) {
                if (role == RecruitOptions.ANY) {
                    EmptyTile(RecruitSlot)
                    Text("Any", color = AppColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    RoleTile(role, RecruitSlot)
                }
            }
        }
    }
    Text("Tap a slot to switch between any, tank, healer and DPS", color = AppColors.muted, fontSize = 12.sp)
}

@Composable
private fun NumberField(value: Int?, placeholder: String, max: Int, modifier: Modifier, onChange: (Int?) -> Unit) {
    PillField(
        value?.toString().orEmpty(),
        placeholder,
        { text -> text.filter(Char::isDigit).take(max.toString().length).toIntOrNull()?.coerceAtMost(max).let(onChange) },
        modifier,
        keyboard = KeyboardType.Number,
    )
}

@Composable
private fun CommentField(value: String, tooLong: Boolean, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(AppColors.background, shape)
            .border(1.dp, if (tooLong) AppColors.red else AppColors.line, shape)
            .padding(12.dp),
    ) {
        if (value.isEmpty()) Text("Say what you're looking for", color = AppColors.muted, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.replace('\n', ' ')) },
            textStyle = TextStyle(color = AppColors.text, fontSize = 14.sp, fontFamily = Jakarta),
            cursorBrush = SolidColor(AppColors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        "${value.encodeToByteArray().size}/${RecruitOptions.MAX_COMMENT}",
        color = if (tooLong) AppColors.red else AppColors.muted,
        fontSize = 12.sp,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PostButton(form: RecruitForm, posting: Boolean, modifier: Modifier) {
    val enabled = form.ready && !posting
    val label = when {
        posting -> "Posting…"
        form.tooLong -> "Shorten the comment"
        !form.ready -> "Pick a duty"
        else -> "Post listing"
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(50))
            .background(AppColors.accent)
            .clickable(enabled = enabled, role = Role.Button) { PartyState.recruit(form) }
            .padding(vertical = 14.dp),
    ) {
        Text(label, color = AppColors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
