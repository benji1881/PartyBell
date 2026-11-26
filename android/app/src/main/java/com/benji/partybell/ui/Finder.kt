package com.benji.partybell.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.AppSettings
import com.benji.partybell.JobIcons
import com.benji.partybell.Listing
import com.benji.partybell.Listings
import com.benji.partybell.PartyState
import com.benji.partybell.Status

private const val FRESH_MS = 60_000L
private const val CLOCK_MS = 30_000L
private const val LIFT = 1.03f
private val SLOT_SIZE = 22.dp
private val STRIPE = 4.dp
private val PracticeColor = Color(0xFF5E9A4E)
private val CompletionColor = Color(0xFF4E82C4)
private val LootColor = Color(0xFFD9853B)
internal const val BEGINNERS = "Beginners welcome"
private const val HIGHLIGHT_ALPHA = 0.4f
internal const val SHOWN_JOBS = 3
private val HighlightIcon = ImageVector.Builder("Highlight", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        PathParser().parsePathString(
            "M6,14l3,3v5h6v-5l3,-3V9H6V14zM11,2h2v3h-2V2zM3.5,5.88l1.41,-1.41l2.12,2.12L5.62,8L3.5,5.88zM16.96,6.59l2.12,-2.12l1.41,1.41L18.38,8L16.96,6.59z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    )
    .build()
private val Sliders = ImageVector.Builder("Sliders", 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString("M3,5h18v2H3zM3,11h18v2H3zM3,17h18v2H3zM14,3h2v6h-2zM6,9h2v6H6zM16,15h2v6h-2z").toNodes(), fill = SolidColor(Color.Black))
    .build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(settings: AppSettings, onSettings: () -> Unit, onTell: (String) -> Unit, wide: Boolean) {
    val all by PartyState.listings.collectAsStateWithLifecycle()
    val finding by PartyState.finding.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableIntStateOf(settings.finderCategory) }
    var fits by rememberSaveable { mutableStateOf(settings.finderFits) }
    var picks by rememberSaveable { mutableStateOf(settings.finderJobs) }
    var tiers by rememberSaveable { mutableStateOf(settings.finderTiers) }
    var words by rememberSaveable { mutableStateOf(settings.finderWords) }
    val jobs by JobIcons.jobs.collectAsStateWithLifecycle()
    var beginners by rememberSaveable { mutableStateOf(settings.finderBeginners) }
    var blocked by rememberSaveable { mutableStateOf(settings.finderBlocked) }
    var goal by rememberSaveable { mutableStateOf(Goal.entries.getOrElse(settings.finderGoal) { Goal.Any }) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val recruit by PartyState.recruit.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val canCreate = recruit.open && status == Status.Connected
    if (creating && canCreate) CreateListing { creating = false }
    val now = rememberNow(CLOCK_MS)
    LaunchedEffect(category, status) {
        val current = PartyState.listings.value[category]
        val stale = current == null || System.currentTimeMillis() - current.time > FRESH_MS
        if (status == Status.Connected && stale) PartyState.find(category)
    }

    val listings = all[category] ?: Listings()
    val loaded = listings.time > 0L
    val matcher = remember(words) { WordMatcher(words) }
    val shown = remember(listings, tiers, fits, picks, beginners, blocked, goal, query, matcher) {
        listings.list
            .filter { it.own || (keepsTier(it, tiers) && keepsJobs(it, fits, picks) && (!beginners || it.beginners) && (blocked || !it.blocked) && goal.keeps(it)) }
            .filter { query.isBlank() || matches(it, query.trim()) }
            .sortedWith(compareByDescending<Listing> { it.own }.thenByDescending { matcher.matches(it.description) })
    }

    val setJobs = { mine: Boolean, picked: Set<String> ->
        fits = mine
        picks = picked
        settings.finderFits = mine
        settings.finderJobs = picked
    }
    val setTiers = { picked: Set<String> -> tiers = picked; settings.finderTiers = picked }
    val setWords = { list: List<String> -> words = list; settings.finderWords = list }
    val setBeginners = { on: Boolean -> beginners = on; settings.finderBeginners = on }
    val setBlocked = { on: Boolean -> blocked = on; settings.finderBlocked = on }
    val setGoal = { picked: Goal -> goal = picked; settings.finderGoal = picked.ordinal }
    val setCategory = { picked: Int -> category = picked; settings.finderCategory = picked }
    val clear = { setJobs(false, emptySet()); setBeginners(false); setBlocked(false); setGoal(Goal.Any) }
    val jobFilter = fits || picks.isNotEmpty()
    val active = listOf(jobFilter, beginners, goal != Goal.Any).count { it }

    Column(Modifier.fillMaxSize()) {
        Header("Finder", onSettings, onRefresh = { PartyState.find(category) }) {
            Text(updated(status, loaded, listings.time, now), color = AppColors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillField(query, "Search duty, leader or description", onChange = { query = it }, modifier = Modifier.weight(1f), icon = Icons.Filled.Search)
            if (!wide) {
                Spacer(Modifier.width(8.dp))
                FilterButton(active) { sheet = true }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (wide) {
            WideFilters(
                category = category,
                tiers = tiers,
                goal = goal,
                fits = fits,
                picks = picks,
                jobs = jobs,
                beginners = beginners,
                blocked = blocked,
                words = words,
                onCategory = setCategory,
                onTiers = setTiers,
                onGoal = setGoal,
                onJobs = setJobs,
                onBeginners = setBeginners,
                onBlocked = setBlocked,
                onWords = setWords,
            )
        } else FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(categoryLabel(category, tiers), true, trailing = Icons.Filled.ArrowDropDown) { sheet = true }
            if (jobFilter) Removable(jobSummary(fits, picks, jobs, SHOWN_JOBS)) { setJobs(false, emptySet()) }
            if (beginners) Removable(BEGINNERS) { setBeginners(false) }
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
            val list = rememberLazyStaggeredGridState()
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(if (wide) 2 else 1),
                state = list,
                verticalItemSpacing = 6.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().fadeEdges(start = list.canScrollBackward, end = false, vertical = true),
            ) {
                if (canCreate) {
                    item(span = StaggeredGridItemSpan.FullLine) { CreateListingCard { creating = true } }
                }
                if (shown.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { Empty(emptyText(status, finding, loaded, listings.list.isEmpty())) }
                }
                items(shown, key = { it.id }) { listing ->
                    ListingCard(
                        listing,
                        listing.secondsLeft(listings.time, now),
                        matcher,
                        onTell = { onTell(listing.peer) },
                        onJoin = { PartyState.join(listing) },
                        onOpen = { PartyState.open(listing.id) },
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (sheet) {
        FilterSheet(
            category = category,
            tiers = tiers,
            goal = goal,
            fits = fits,
            picks = picks,
            jobs = jobs,
            beginners = beginners,
            blocked = blocked,
            words = words,
            count = shown.size,
            onCategory = setCategory,
            onTiers = setTiers,
            onGoal = setGoal,
            onJobs = setJobs,
            onBeginners = setBeginners,
            onBlocked = setBlocked,
            onWords = setWords,
            onClear = clear,
            onDismiss = { sheet = false },
        )
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
internal fun PillField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onDone: (() -> Unit)? = null,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(44.dp)
            .background(AppColors.card, shape)
            .border(1.dp, AppColors.line, shape)
            .padding(horizontal = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = AppColors.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = onDone?.let { done -> { done() } }),
                textStyle = TextStyle(color = AppColors.text, fontSize = 14.sp, fontFamily = Jakarta),
                cursorBrush = SolidColor(AppColors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun Chip(label: String, active: Boolean, modifier: Modifier = Modifier, trailing: ImageVector? = null, onClick: () -> Unit) {
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
private fun ListingCard(listing: Listing, seconds: Int, matcher: WordMatcher, onTell: () -> Unit, onJoin: () -> Unit, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val ranges = remember(listing.description, matcher) { matcher.ranges(listing.description) }
    var hidden by remember(ranges) { mutableStateOf(false) }
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
                .clip(shape)
                .background(AppColors.card)
                .drawBehind { objective?.let { drawRect(it, size = Size(STRIPE.toPx(), size.height)) } }
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
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
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
                if (hidden) {
                    Icon(
                        HighlightIcon,
                        contentDescription = "Highlighted word",
                        tint = AppColors.yellow,
                        modifier = Modifier.padding(top = 3.dp, end = 4.dp).size(14.dp),
                    )
                }
                if (listing.locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Password",
                        tint = AppColors.muted,
                        modifier = Modifier.padding(top = 3.dp, end = 4.dp).size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val slots = listing.slots.sortedWith(compareBy({ roleRank(it.role) }, { !it.filled }))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                slots.forEach { if (it.filled) JobTile(it.job, it.role, it.job, SLOT_SIZE) else RoleTile(it.role, SLOT_SIZE) }
            }
            if (listing.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                val highlight = AppColors.yellow.copy(alpha = HIGHLIGHT_ALPHA)
                val text = remember(listing.description, ranges, highlight) {
                    buildAnnotatedString {
                        append(listing.description)
                        ranges.forEach { addStyle(SpanStyle(background = highlight), it.first, it.last + 1) }
                    }
                }
                Text(
                    text,
                    color = AppColors.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        val end = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
                        hidden = ranges.isNotEmpty() && ranges.none { it.last < end }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val tags = tags(listing)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { Tag(it) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Text(
                        "${listing.leader} · ${listing.world}",
                        color = AppColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !listing.own, onClick = onTell)
                            .padding(vertical = 2.dp),
                    )
                }
                Text(remaining(seconds), color = AppColors.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        SpringMenu(
            expanded = menu,
            onDismiss = { menu = false },
            items = listOfNotNull(
                MenuItem("Send tell", Icons.AutoMirrored.Filled.Send, onTell).takeIf { !listing.own },
                MenuItem("Join party", Icons.Filled.Add, onJoin).takeIf { !listing.own && !listing.locked },
                MenuItem("Open in Party Finder", Icons.Filled.Search, onOpen),
                MenuItem("Share listing", Icons.Filled.Share) { share(context, shareText(listing, seconds)) },
            ),
        )
    }
}

private fun shareText(listing: Listing, seconds: Int): String {
    val details = listOfNotNull(
        when {
            listing.practice -> "Practice"
            listing.completion -> "Duty completion"
            listing.loot -> "Loot"
            else -> null
        },
        "Min ilvl ${listing.ilvl}".takeIf { listing.ilvl > 0 },
        "${remaining(seconds)} left",
    )
    val open = listing.slots.filterNot { it.filled }
        .sortedBy { roleRank(it.role) }
        .groupingBy { roleName(it.role) }
        .eachCount()
        .map { (role, count) -> "$count ${openRole(role, count)}" }
    return listOfNotNull(
        "**${listing.title}**",
        details.joinToString(" · "),
        "Looking for ${open.joinToString(", ")}".takeIf { open.isNotEmpty() },
        listing.description.trim().lines().joinToString("\n") { "> $it" }.takeIf { listing.description.isNotBlank() },
        "Leader: ${listing.leader} · ${listing.world}",
    ).joinToString("\n")
}

private fun openRole(role: String, count: Int) = when (role) {
    "dps" -> "DPS"
    "any" -> "any role"
    else -> if (count == 1) role else "${role}s"
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null))
}

private fun objective(listing: Listing): Color? = when {
    listing.practice -> PracticeColor
    listing.completion -> CompletionColor
    listing.loot -> LootColor
    else -> null
}

private class TagInfo(val label: String, val color: Color? = null, val icon: ImageBitmap? = null)

@Composable
private fun tags(listing: Listing): List<TagInfo> {
    val sprout by JobIcons.sprout.collectAsStateWithLifecycle()
    return listOfNotNull(
        TagInfo("Blocked", AppColors.red).takeIf { listing.blocked },
        TagInfo("Fits your job", AppColors.accent).takeIf { listing.fits && !listing.own },
        TagInfo("Practice", PracticeColor).takeIf { listing.practice },
        TagInfo("Duty completion", CompletionColor).takeIf { listing.completion },
        TagInfo("Loot", LootColor).takeIf { listing.loot },
        TagInfo("Duty complete").takeIf { listing.cleared },
        TagInfo(BEGINNERS, icon = sprout).takeIf { listing.beginners },
        TagInfo("Min ilvl ${listing.ilvl}").takeIf { listing.ilvl > 0 },
    )
}

@Composable
private fun Tag(tag: TagInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(tag.color ?: AppColors.line, RoundedCornerShape(50))
            .padding(start = if (tag.icon != null) 5.dp else 8.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
    ) {
        if (tag.icon != null) {
            Image(tag.icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(tag.label, color = if (tag.color != null) Color.White else AppColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
