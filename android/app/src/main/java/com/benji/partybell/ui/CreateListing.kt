package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.PartyState
import com.benji.partybell.RecruitDuty
import com.benji.partybell.RecruitForm
import com.benji.partybell.RecruitOptions

private const val DISABLED_ALPHA = 0.45f
private const val LIT_ALPHA = 0.16f
private const val PARTY_WEIGHT = 1.4f
private val DutyListHeight = 420.dp
private val ModesWidth = 420.dp
private val PostWidth = 200.dp

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

private enum class Picker { CATEGORY, DUTY, OBJECTIVE, COMPLETION, LOOT }

@Composable
internal fun CreateListing(onClose: () -> Unit) {
    val recruit by PartyState.recruit.collectAsStateWithLifecycle()
    val posting by PartyState.posting.collectAsStateWithLifecycle()
    var form by remember { mutableStateOf(RecruitForm()) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    val duties = recruit.duties.filter { it.category == form.category && it.mode == form.mode }
    val categories = RecruitOptions.categories(form.mode)
    LaunchedEffect(Unit) { PartyState.posted.collect { onClose() } }
    LaunchedEffect(recruit.open) { if (!recruit.open && !posting) onClose() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(AppColors.background).safeDrawingPadding().imePadding()) {
            val wide = isWide()
            val modes: @Composable (Modifier) -> Unit = { modifier ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
                    RecruitOptions.modes.forEach { (mode, name) ->
                        Chip(name, form.mode == mode, Modifier.weight(1f)) { if (form.mode != mode) form = form.withMode(mode) }
                    }
                }
            }
            val duty: @Composable () -> Unit = {
                Section("Duty") {
                    if (categories.size > 1) {
                        PickRow("Category", label(categories, form.category)) { picker = Picker.CATEGORY }
                        Line()
                    }
                    if (RecruitOptions.needsDuty(form.category)) {
                        PickRow("Duty", duties.firstOrNull { it.id == form.duty }?.name ?: "Pick a duty") { picker = Picker.DUTY }
                    }
                }
            }
            val party: @Composable () -> Unit = {
                Card {
                    PartySlots(form.slots, form.mode, RecruitOptions.groups(form.mode, form.category), form.noSpectators) { form = form.copy(slots = it) }
                    if (form.mode == RecruitOptions.CUSTOM_MATCH) {
                        Spacer(Modifier.height(10.dp))
                        LightButton("Remove spectators from search", form.noSpectators, AppColors.accent, Modifier.fillMaxWidth()) {
                            form = form.copy(noSpectators = !form.noSpectators)
                        }
                    }
                }
            }
            val comment: @Composable () -> Unit = {
                Section("Comment") {
                    CommentField(form.comment, form.tooLong) { form = form.copy(comment = it) }
                }
            }
            val conditions: @Composable () -> Unit = {
                Section("Conditions") {
                    PickRow("Objective", label(RecruitOptions.objectives, form.objective)) { picker = Picker.OBJECTIVE }
                    Line()
                    PickRow("Completion status", label(RecruitOptions.completions, form.completion)) { picker = Picker.COMPLETION }
                    Line()
                    PickRow("Loot", label(RecruitOptions.loots, form.loot)) { picker = Picker.LOOT }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(form.itemLevel.takeIf { it > 0 }, "Min item level", RecruitOptions.MAX_ITEM_LEVEL, Modifier.weight(1f)) {
                            form = form.copy(itemLevel = it ?: 0)
                        }
                        NumberField(form.password, "Password", RecruitOptions.MAX_PASSWORD, Modifier.weight(1f)) {
                            form = form.copy(password = it)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LightButton("Beginners welcome", form.beginners, roleColor(ROLE_HEALER), Modifier.weight(1f)) { form = form.copy(beginners = !form.beginners) }
                        LightButton("One player per job", form.onePerJob, AppColors.yellow, Modifier.weight(1f)) { form = form.copy(onePerJob = !form.onePerJob) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                CircleButton(Icons.Filled.Close, "Close", onClose)
                Text("Create listing", fontFamily = Fraunces, fontSize = 24.sp, color = AppColors.text)
                if (wide) {
                    Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        modes(Modifier.widthIn(max = ModesWidth).fillMaxWidth())
                    }
                    PostButton(form, posting, Modifier.width(PostWidth))
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    FormColumn(Modifier.weight(1f)) {
                        duty()
                        conditions()
                        comment()
                    }
                    FormColumn(Modifier.weight(PARTY_WEIGHT)) { party() }
                }
            } else {
                FormColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    modes(Modifier.fillMaxWidth())
                    duty()
                    party()
                    comment()
                    conditions()
                }
                PostButton(form, posting, Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
    when (picker) {
        Picker.CATEGORY -> OptionSheet("Category", categories, form.category, { picker = null }) { form = form.withCategory(it) }
        Picker.DUTY -> DutySheet(duties, form.duty, { picker = null }) { form = form.copy(duty = it) }
        Picker.OBJECTIVE -> OptionSheet("Objective", RecruitOptions.objectives, form.objective, { picker = null }) { form = form.copy(objective = it) }
        Picker.COMPLETION -> OptionSheet("Completion status", RecruitOptions.completions, form.completion, { picker = null }) { form = form.copy(completion = it) }
        Picker.LOOT -> OptionSheet("Loot", RecruitOptions.loots, form.loot, { picker = null }) { form = form.copy(loot = it) }
        null -> Unit
    }
}

@Composable
private fun FormColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
    ) {
        content()
    }
}

private fun label(options: List<Pair<Int, String>>, value: Int) = options.firstOrNull { it.first == value }?.second.orEmpty()

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card {
        SheetLabel(title)
        content()
    }
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = AppColors.muted, fontSize = 12.sp)
            Text(value, color = AppColors.text, fontSize = 15.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun OptionSheet(title: String, options: List<Pair<Int, String>>, picked: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    SubSheet(title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                OptionRow(label, value == picked) {
                    onPick(value)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun DutySheet(duties: List<RecruitDuty>, picked: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = duties.filter { it.name.contains(query.trim(), ignoreCase = true) }
    SubSheet("Duty", onDismiss) {
        PillField(query, "Search duties", { query = it }, Modifier.fillMaxWidth(), Icons.Filled.Search)
        Spacer(Modifier.height(8.dp))
        when {
            duties.isEmpty() -> Text("No duties unlocked in this category", color = AppColors.muted, fontSize = 13.sp)
            matches.isEmpty() -> Text("No duty matches", color = AppColors.muted, fontSize = 13.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = DutyListHeight)) {
            items(matches, key = { it.id }) { duty ->
                OptionRow(duty.name, duty.id == picked) {
                    onPick(duty.id)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) AppColors.accent else AppColors.card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = if (active) AppColors.onAccent else AppColors.text,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.onAccent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LightButton(label: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (on) color.copy(alpha = LIT_ALPHA) else AppColors.card)
            .border(1.dp, if (on) color else AppColors.line, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (on) color else AppColors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
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
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
    ) {
        if (value.isEmpty()) Text("Say what you're looking for", color = AppColors.muted, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.replace('\n', ' ')) },
            textStyle = TextStyle(color = AppColors.text, fontSize = 14.sp, fontFamily = Jakarta),
            cursorBrush = SolidColor(AppColors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${value.encodeToByteArray().size}/${RecruitOptions.MAX_COMMENT}",
            color = if (tooLong) AppColors.red else AppColors.muted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomEnd).offset(y = 20.dp),
        )
    }
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
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(50))
            .background(AppColors.accent)
            .clickable(enabled = enabled, role = Role.Button) { PartyState.recruit(form) }
            .padding(vertical = 14.dp),
    ) {
        Text(label, color = AppColors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
