package com.benji.partybell.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.AppSettings
import com.benji.partybell.FinderCategory
import com.benji.partybell.Listing
import com.benji.partybell.Listings
import com.benji.partybell.PartyState
import com.benji.partybell.Status
import kotlinx.coroutines.delay

private const val FRESH_MS = 60_000L
private const val CLOCK_MS = 30_000L
private const val LIFT = 1.03f
private val OpenSlot = Color(0xFFB9B4A8)
private val SLOT_SIZE = 26.dp
private val PracticeColor = Color(0xFF5E9A4E)
private val CompletionColor = Color(0xFF4E82C4)
private val LootColor = Color(0xFFD9853B)
private const val GRID_COLUMNS = 2
private val Sliders = ImageVector.Builder("Sliders", 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString("M3,5h18v2H3zM3,11h18v2H3zM3,17h18v2H3zM14,3h2v6h-2zM6,9h2v6H6zM16,15h2v6h-2z").toNodes(), fill = SolidColor(Color.Black))
    .build()

private enum class Goal(val label: String, val keeps: (Listing) -> Boolean) {
    Any("Any", { true }),
    Practice("Practice", { it.practice }),
    Completion("Completion", { it.completion }),
    Loot("Loot", { it.loot }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(settings: AppSettings, onSettings: () -> Unit, onTell: (String) -> Unit) {
    val all by PartyState.listings.collectAsStateWithLifecycle()
    val finding by PartyState.finding.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableIntStateOf(settings.finderCategory) }
    var fits by rememberSaveable { mutableStateOf(settings.finderFits) }
    var beginners by rememberSaveable { mutableStateOf(settings.finderBeginners) }
    var goal by rememberSaveable { mutableStateOf(Goal.entries.getOrElse(settings.finderGoal) { Goal.Any }) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(CLOCK_MS)
        }
    }
    LaunchedEffect(category, status) {
        val current = PartyState.listings.value[category]
        val stale = current == null || System.currentTimeMillis() - current.time > FRESH_MS
        if (status == Status.Connected && stale) PartyState.find(category)
    }

    val listings = all[category] ?: Listings()
    val loaded = listings.time > 0L
    val shown = remember(listings, fits, beginners, goal, query) {
        listings.list
            .filter { it.own || ((!fits || it.fits) && (!beginners || it.beginners) && goal.keeps(it)) }
            .filter { query.isBlank() || matches(it, query.trim()) }
            .sortedByDescending { it.own }
    }

    val setFits = { on: Boolean -> fits = on; settings.finderFits = on }
    val setBeginners = { on: Boolean -> beginners = on; settings.finderBeginners = on }
    val setGoal = { picked: Goal -> goal = picked; settings.finderGoal = picked.ordinal }
    val clear = { setFits(false); setBeginners(false); setGoal(Goal.Any) }
    val active = listOf(fits, beginners, goal != Goal.Any).count { it }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Finder", fontFamily = Fraunces, fontSize = 28.sp, color = AppColors.text)
                Text(updated(status, loaded, listings.time, now), color = AppColors.muted, fontSize = 13.sp)
            }
            CircleButton(Icons.Filled.Refresh, "Refresh", { PartyState.find(category) })
            Spacer(Modifier.width(8.dp))
            CircleButton(Icons.Filled.Settings, "Settings", onSettings)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, onChange = { query = it }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilterButton(active) { sheet = true }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(FinderCategory.label(category), true, trailing = Icons.Filled.ArrowDropDown) { sheet = true }
            if (fits) Removable("Fits my job") { setFits(false) }
            if (beginners) Removable("Beginners welcome") { setBeginners(false) }
            if (goal != Goal.Any) Removable(goal.label) { setGoal(Goal.Any) }
            if (active > 0) {
                Text(
                    "Clear",
                    color = AppColors.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = clear)
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PullToRefreshBox(
            isRefreshing = finding,
            onRefresh = { PartyState.find(category) },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shown.isEmpty()) {
                    item { Empty(emptyText(status, finding, loaded, listings.list.isEmpty())) }
                }
                items(shown, key = { it.id }) { listing ->
                    ListingCard(
                        listing,
                        listing.secondsLeft(listings.time, now),
                        onTell = { onTell(listing.peer) },
                        onJoin = { PartyState.join(listing) },
                        onOpen = { PartyState.open(listing) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (sheet) {
        FilterSheet(
            category = category,
            goal = goal,
            fits = fits,
            beginners = beginners,
            count = shown.size,
            onCategory = {
                category = it
                settings.finderCategory = it
            },
            onGoal = setGoal,
            onFits = setFits,
            onBeginners = setBeginners,
            onClear = clear,
            onDismiss = { sheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    category: Int,
    goal: Goal,
    fits: Boolean,
    beginners: Boolean,
    count: Int,
    onCategory: (Int) -> Unit,
    onGoal: (Goal) -> Unit,
    onFits: (Boolean) -> Unit,
    onBeginners: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FinderCategory.all.chunked(GRID_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (id, label) ->
                            Chip(label, id == category, Modifier.weight(1f)) { onCategory(id) }
                        }
                    }
                }
            }
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
            ToggleRow("Fits my job", fits, onFits)
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.line))
            ToggleRow("Beginners welcome", beginners, onBeginners)
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
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 6.dp),
    ) {
        Text(label, color = AppColors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun FilterButton(active: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val on = active > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (on) AppColors.accent else AppColors.card)
            .border(1.dp, if (on) AppColors.accent else AppColors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
    ) {
        Icon(Sliders, contentDescription = "Filters", tint = if (on) AppColors.onAccent else AppColors.text, modifier = Modifier.size(18.dp))
        if (on) {
            Spacer(Modifier.width(6.dp))
            Text("$active", color = AppColors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Removable(label: String, onRemove: () -> Unit) {
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

private fun matches(listing: Listing, query: String) =
    listOf(listing.title, listing.description, listing.leader).any { it.contains(query, ignoreCase = true) } || codeMatches(listing.code, query)

private fun codeMatches(code: String, query: String) =
    code.startsWith(query, ignoreCase = true) && code.getOrNull(query.length)?.isDigit() != true && query.any { it.isDigit() }

private fun updated(status: Status, loaded: Boolean, time: Long, now: Long): String = when {
    status != Status.Connected -> status.label
    !loaded -> "Not loaded yet"
    now - time < FRESH_MS -> "Updated just now"
    else -> "Updated ${(now - time) / FRESH_MS} min ago"
}

private fun emptyText(status: Status, finding: Boolean, loaded: Boolean, none: Boolean): String = when {
    finding -> "Loading listings…"
    status != Status.Connected -> "Listings show up here while the game is connected"
    !loaded -> "Pull down to load listings"
    none -> "No listings in this category"
    else -> "No listings match"
}

private fun remaining(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
    seconds >= 60 -> "${seconds / 60} min"
    else -> "<1 min"
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(44.dp)
            .background(AppColors.card, shape)
            .border(1.dp, AppColors.line, shape)
            .padding(horizontal = 14.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search duty, leader or description", color = AppColors.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = AppColors.text, fontSize = 14.sp, fontFamily = Jakarta),
                cursorBrush = SolidColor(AppColors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, modifier: Modifier = Modifier, trailing: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val color = if (active) AppColors.onAccent else AppColors.text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(if (active) AppColors.accent else AppColors.card)
            .border(1.dp, if (active) AppColors.accent else AppColors.line, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = if (trailing != null) 8.dp else 12.dp, top = 7.dp, bottom = 7.dp),
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ListingCard(listing: Listing, seconds: Int, onTell: () -> Unit, onJoin: () -> Unit, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(
        if (menu) LIFT else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lift",
    )
    val objective = objective(listing)
    val border = when {
        listing.own -> AppColors.accent
        objective != null -> objective.copy(alpha = 0.5f)
        else -> AppColors.line
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                }
                .background(AppColors.card, shape)
                .background(objective?.copy(alpha = 0.12f) ?: Color.Transparent, shape)
                .border(1.dp, border, shape)
                .pointerInput(listing.id) {
                    detectTapGestures(
                        onDoubleTap = { onOpen() },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (listing.own) {
                Text("YOUR LISTING", color = AppColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
            }
            Row {
                Text(
                    listing.title,
                    color = AppColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (listing.locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Password",
                        tint = AppColors.muted,
                        modifier = Modifier.padding(top = 3.dp, end = 4.dp).size(14.dp),
                    )
                }
                Text(remaining(seconds), color = AppColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            val slots = listing.slots.sortedWith(compareBy({ roleRank(it.role) }, { !it.filled }))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                slots.forEach { if (it.filled) JobTile(it.job, it.role, it.job, SLOT_SIZE) else RoleTile(it.role, SLOT_SIZE) }
            }
            if (listing.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    listing.description,
                    color = AppColors.text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.background.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            val tags = tags(listing)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { (label, color) -> Tag(label, color) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${listing.leader} · ${listing.world}",
                color = AppColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !listing.own, onClick = onTell)
                    .padding(vertical = 2.dp),
            )
        }
        SpringMenu(
            expanded = menu,
            onDismiss = { menu = false },
            items = listOfNotNull(
                MenuItem("Send tell", Icons.AutoMirrored.Filled.Send, onTell).takeIf { !listing.own },
                MenuItem("Join party", Icons.Filled.Add, onJoin).takeIf { !listing.own && !listing.locked },
                MenuItem("Open in Party Finder", Icons.Filled.Search, onOpen),
            ),
        )
    }
}

private fun objective(listing: Listing): Color? = when {
    listing.practice -> PracticeColor
    listing.completion -> CompletionColor
    listing.loot -> LootColor
    else -> null
}

@Composable
private fun tags(listing: Listing): List<Pair<String, Color?>> = listOfNotNull(
    ("Fits your job" to AppColors.accent).takeIf { listing.fits && !listing.own },
    ("Practice" to PracticeColor).takeIf { listing.practice },
    ("Duty completion" to CompletionColor).takeIf { listing.completion },
    ("Loot" to LootColor).takeIf { listing.loot },
    ("Duty complete" to null).takeIf { listing.cleared },
    ("Beginners welcome" to null).takeIf { listing.beginners },
    ("Min ilvl ${listing.ilvl}" to null).takeIf { listing.ilvl > 0 },
    ("Cross-world" to null).takeIf { listing.crossWorld },
)

@Composable
private fun Tag(label: String, color: Color?) {
    Text(
        label,
        color = if (color != null) Color.White else AppColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color ?: AppColors.line, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun SlotBars(filled: List<Int?>, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        filled.forEach { role ->
            val color = if (role != null) roleColor(role) else OpenSlot.copy(alpha = 0.5f)
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(50)),
            )
        }
    }
}
