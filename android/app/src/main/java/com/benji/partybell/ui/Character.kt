package com.benji.partybell.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.Reasons
import com.benji.partybell.AppSettings
import com.benji.partybell.Bis
import com.benji.partybell.BisSlot
import com.benji.partybell.Currency
import com.benji.partybell.GearPiece
import com.benji.partybell.ItemStat
import com.benji.partybell.ItemIcons
import com.benji.partybell.JobIcons
import com.benji.partybell.PartyState
import com.benji.partybell.Profile
import com.benji.partybell.Status
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat

private const val MAX_CURRENCIES = 3
private const val REPAIR_BELOW = 10
internal const val FULL = 100
internal const val RING_MS = 900
internal const val FADE_MS = 400
private const val LOAD_MS = 5_000L
private const val TRACK_ALPHA = 0.35f
internal const val EMPTY_ALPHA = 0.35f
internal const val TABULAR = "tnum"
private const val MILLION = 1_000_000L
private const val SHORT_DIGITS = 3
private val HeroTile = 64.dp
private val HeroGap = 5.dp
internal val SlotSize = 44.dp
private val SheetSlotSize = 48.dp
private val Ink = Color(0xFF2B2F2A)

internal data class SlotInfo(val index: Int, val symbol: String, val label: String)

internal val Weapons = listOf(
    SlotInfo(0, "main", "Main hand"),
    SlotInfo(1, "off", "Off hand"),
)

internal val Armour = listOf(
    SlotInfo(2, "head", "Head"),
    SlotInfo(3, "body", "Body"),
    SlotInfo(4, "hands", "Hands"),
    SlotInfo(6, "legs", "Legs"),
    SlotInfo(7, "feet", "Feet"),
)

internal val Accessories = listOf(
    SlotInfo(8, "ears", "Earrings"),
    SlotInfo(9, "neck", "Necklace"),
    SlotInfo(10, "wrists", "Bracelets"),
    SlotInfo(11, "ring", "Ring"),
    SlotInfo(12, "ring", "Ring"),
)

private val LeftSlots = Weapons.take(1) + Armour
private val RightSlots = Weapons.drop(1) + Accessories
internal val AllSlots = Weapons + Armour + Accessories

internal val GearPiece.needsRepair get() = condition < REPAIR_BELOW

internal fun Profile.piece(slot: Int?) = gear.firstOrNull { it.slot == slot }

internal fun Profile.home() = listOf(world, dataCenter).filter { it.isNotEmpty() }.joinToString(" · ")

internal fun Profile.toNext() = if (!max && expNeeded > 0) "${exp * FULL / expNeeded}% to level ${level + 1}" else null

internal fun Profile?.shown(picked: List<Long>) = picked.mapNotNull { id -> this?.currencies?.firstOrNull { it.id == id } }.take(MAX_CURRENCIES)

internal fun GearPiece.detail(info: SlotInfo) = "${category.ifEmpty { info.label }} · Item level $level"

internal fun Profile?.repairs() = this?.gear?.count { it.needsRepair } ?: 0

internal fun repairLabel(repairs: Int) = "$repairs need repair"

@Composable
fun CharacterScreen(settings: AppSettings, onSettings: () -> Unit, wide: Boolean, tabletop: Tabletop?) {
    val character by PartyState.character.collectAsStateWithLifecycle()
    val bis by PartyState.bis.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf(settings.characterCurrencies) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var slot by rememberSaveable { mutableStateOf<Int?>(null) }
    var idle by remember { mutableStateOf(false) }
    var upgrades by rememberSaveable { mutableStateOf(false) }
    var linking by rememberSaveable { mutableStateOf(false) }
    val current = character

    LaunchedEffect(current == null, status) {
        idle = false
        if (current == null && status == Status.Connected) {
            delay(LOAD_MS)
            idle = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Character", onSettings) { StatusLine(status) }
        when {
            status != Status.Connected -> Empty(Reasons.NOT_CONNECTED, Modifier.weight(1f))
            current == null && idle -> Empty("Log in to a character in the game", Modifier.weight(1f))
            tabletop != null -> CharacterTabletop(current, tabletop, picked, slot, Modifier.weight(1f), onSlot = { slot = it.takeIf { it != slot } }, onPick = { picking = true })
            wide -> CharacterColumns(current, bis, picked, Modifier.weight(1f), onSlot = { slot = it }, onPick = { picking = true }, onLink = { linking = true })
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 14.dp),
            ) {
                HeroCard(current, picked, onPick = { picking = true })
                LocationCard(current)
                GearCard(current, bis, onSlot = { slot = it }, onBis = { if (bis.set != null) upgrades = true else linking = true })
            }
        }
    }

    if (picking && current != null) {
        CurrencySheet(current.currencies, picked, onPick = {
            picked = it
            settings.characterCurrencies = it
        }, onDismiss = { picking = false })
    }
    val piece = current?.piece(slot)
    val info = AllSlots.firstOrNull { it.index == slot }
    if (tabletop == null && piece != null && info != null) GearSheet(piece, info, bis.set?.slot(info.index), onDismiss = { slot = null })
    val set = bis.set
    if (upgrades && set != null) UpgradesSheet(set, current, onChange = { upgrades = false; linking = true }, onDismiss = { upgrades = false })
    if (linking && current != null) LinkSheet(current.jobName, bis.link, onDismiss = { linking = false })
}

@Composable
private fun HeroCard(character: Profile?, picked: List<Long>, onPick: () -> Unit) {
    val fade = animated(if (character != null) 1f else 0f, FADE_MS)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroJob(character)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).alpha(fade)) {
                if (character != null) {
                    LevelPill(character)
                    Text(character.name, fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(character.home(), color = AppColors.muted, fontSize = 13.sp)
                    character.toNext()?.let { Text(it, color = AppColors.muted, fontSize = 12.sp) }
                }
            }
        }
        Line()
        val shown = character.shown(picked)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .alpha(fade)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = character != null, role = Role.Button, onClickLabel = "Pick currencies", onClick = onPick),
        ) {
            when {
                character == null -> Unit
                shown.isEmpty() -> Text("Pick currencies to show", color = AppColors.muted, fontSize = 13.sp)
                else -> shown.forEachIndexed { index, currency ->
                    if (index > 0) Box(Modifier.padding(horizontal = 10.dp).width(1.dp).fillMaxHeight().background(AppColors.line))
                    CurrencyColumn(currency, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun LevelPill(character: Profile) {
    Text(
        "Lv ${character.level} ${character.jobName}",
        color = Ink,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.background(AppColors.yellow, CircleShape).padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

@Composable
internal fun HeroJob(character: Profile?) {
    val progress = animated(if (character != null && !character.max && character.expNeeded > 0) character.exp.toFloat() / character.expNeeded else 0f, RING_MS)
    val fade = animated(if (character != null) 1f else 0f, FADE_MS)
    val track = AppColors.muted.copy(alpha = TRACK_ALPHA)
    val accent = AppColors.accent
    val levelling = character != null && !character.max
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(HeroTile + HeroGap * 2).drawBehind {
            if (!levelling) return@drawBehind
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val radius = (HeroTile * CORNER_RATIO + HeroGap).toPx()
            val outline = Path().apply {
                addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(radius)))
            }
            drawPath(outline, track, style = Stroke(stroke))
            val measure = PathMeasure().apply { setPath(outline, false) }
            val done = Path()
            measure.getSegment(0f, measure.length * progress, done, true)
            drawPath(done, accent, style = Stroke(stroke, cap = StrokeCap.Round))
        },
    ) {
        if (character == null) {
            EmptyTile(HeroTile, dashed = false)
        } else {
            JobTile(character.job, character.role, character.name, HeroTile, Modifier.alpha(fade))
        }
    }
}

@Composable
private fun CurrencyColumn(currency: Currency, modifier: Modifier) {
    val icons by JobIcons.currencies.collectAsStateWithLifecycle()
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icons[currency.id.toString()]?.let {
            Image(it, contentDescription = currency.name, filterQuality = FilterQuality.High, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            short(currency.count),
            color = AppColors.text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR),
        )
    }
}

internal fun short(count: Long): String = if (count < MILLION) {
    NumberFormat.getIntegerInstance().format(count)
} else {
    BigDecimal(count).divide(BigDecimal(MILLION)).round(MathContext(SHORT_DIGITS, RoundingMode.DOWN)).stripTrailingZeros().toPlainString() + "M"
}

@Composable
internal fun LocationCard(character: Profile?, modifier: Modifier = Modifier) {
    val symbols by JobIcons.symbols.collectAsStateWithLifecycle()
    val fade = animated(if (character != null) 1f else 0f, FADE_MS)
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                symbols["aetheryte"]?.let { Glyph(it, AppColors.accent, Modifier.fillMaxSize().alpha(fade)) }
            }
            Spacer(Modifier.width(12.dp))
            if (character != null) {
                Column(Modifier.alpha(fade)) {
                    Text(character.zone, color = AppColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = listOfNotNull(character.area.ifEmpty { null }, if (character.instance > 0) "Instance ${character.instance}" else null)
                    if (detail.isNotEmpty()) Text(detail.joinToString(" · "), color = AppColors.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun GearCard(character: Profile?, bis: Bis, onSlot: (Int) -> Unit, onBis: () -> Unit) {
    val fade = animated(if (character != null) 1f else 0f, FADE_MS)
    val repairs = character.repairs()
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlotColumn(LeftSlots, character, bis, onSlot)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).alpha(fade)) {
                if (character != null) {
                    Text("${character.itemLevel}", fontFamily = Fraunces, fontSize = 34.sp, color = AppColors.text)
                    Text("Average item level", color = AppColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    UpgradeButton(bis, onBis)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (repairs > 0) repairLabel(repairs) else "Nothing to repair",
                        color = if (repairs > 0) AppColors.red else AppColors.muted,
                        fontWeight = if (repairs > 0) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.5.sp,
                    )
                }
            }
            SlotColumn(RightSlots, character, bis, onSlot)
        }
    }
}

@Composable
private fun SlotColumn(slots: List<SlotInfo>, character: Profile?, bis: Bis, onSlot: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { SlotButton(it, character, AppColors.card, onSlot, bis.pending(it.index) != null) }
    }
}

@Composable
internal fun SlotButton(info: SlotInfo, character: Profile?, surface: Color, onSlot: (Int) -> Unit, upgrade: Boolean = false) {
    val piece = character?.piece(info.index)
    GearSlot(info, piece, character != null, SlotSize, surface, Modifier.clickable(enabled = piece != null, role = Role.Button, onClickLabel = info.label) { onSlot(info.index) }, upgrade)
}

@Composable
internal fun GearSlot(info: SlotInfo, piece: GearPiece?, loaded: Boolean, size: Dp, surface: Color, modifier: Modifier = Modifier, upgrade: Boolean = false) {
    val symbols by JobIcons.symbols.collectAsStateWithLifecycle()
    val items by ItemIcons.icons.collectAsStateWithLifecycle()
    val progress = animated((piece?.condition ?: 0).coerceIn(0, FULL) / FULL.toFloat(), RING_MS)
    val fade = animated(if (loaded) 1f else 0f, FADE_MS)
    val color = if (piece?.needsRepair == true) AppColors.red else AppColors.accent
    val icon = piece?.let { items[it.icon] }
    Box(Modifier.size(size)) {
        Ring(progress, color, size, Modifier.clip(CircleShape).then(modifier)) {
            if (icon != null) {
                Image(icon, contentDescription = piece.name, filterQuality = FilterQuality.High, modifier = Modifier.size(size * 0.74f).clip(CircleShape).alpha(fade))
            } else {
                symbols[info.symbol]?.let {
                    Glyph(it, AppColors.text, Modifier.size(size * 0.56f).alpha(fade * if (piece == null) EMPTY_ALPHA else 1f))
                }
            }
        }
        if (piece != null && piece.spiritbond >= FULL) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(size * 0.28f)
                    .alpha(fade)
                    .background(surface, CircleShape)
                    .padding(size * 0.05f)
                    .background(AppColors.yellow, CircleShape),
            )
        }
        if (upgrade) UpgradeTab(size, surface, Modifier.align(Alignment.BottomCenter).offset(y = size * 0.12f).alpha(fade))
    }
}

@Composable
internal fun Ring(progress: Float, color: Color, size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val track = AppColors.muted.copy(alpha = TRACK_ALPHA)
    Box(
        contentAlignment = Alignment.Center,
        content = content,
        modifier = modifier.size(size).drawBehind {
            val stroke = (size * 0.06f).toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (progress > 0f) drawArc(color, -90f, 360f * progress, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GearSheet(piece: GearPiece, info: SlotInfo, bis: BisSlot?, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GearSlot(info, piece, true, SheetSlotSize, AppColors.background, upgrade = bis?.done == false)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(piece.name, fontFamily = Fraunces, fontSize = 20.sp, color = AppColors.text)
                    Text(piece.detail(info), color = AppColors.muted, fontSize = 13.sp)
                }
            }
            if (piece.stats.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    piece.stats.forEach { StatBox(it, Modifier.weight(1f)) }
                }
            }
            if (piece.bonuses.isNotEmpty()) {
                Text("Bonuses", color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
                piece.bonuses.forEach { bonus ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(bonus.name, color = AppColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("+${bonus.value}", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR))
                    }
                }
            }
            bis?.let { BisCompare(piece, it) }
            Line(Modifier.padding(vertical = 16.dp))
            PieceMeters(piece)
            if (piece.spiritbond >= FULL) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Dot(AppColors.yellow)
                    Spacer(Modifier.width(8.dp))
                    Text("Ready for materia extraction", color = AppColors.text, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatBox(stat: ItemStat, modifier: Modifier) {
    Column(modifier.background(AppColors.card, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(stat.name, color = AppColors.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stat.value, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR))
    }
}

@Composable
internal fun PieceMeters(piece: GearPiece) {
    Meter("Condition", piece.condition, if (piece.needsRepair) AppColors.red else AppColors.accent)
    Spacer(Modifier.height(14.dp))
    Meter("Spiritbond", piece.spiritbond, AppColors.yellow)
}

@Composable
private fun Meter(label: String, percent: Int, color: Color) {
    Row {
        Text(label, color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("$percent%", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR))
    }
    Bar(percent, color, Modifier.padding(top = 6.dp).fillMaxWidth())
}

@Composable
internal fun Bar(percent: Int, color: Color, modifier: Modifier) {
    Box(modifier.height(6.dp).clip(CircleShape).background(AppColors.line)) {
        Box(Modifier.fillMaxWidth(percent.coerceIn(0, FULL) / FULL.toFloat()).fillMaxHeight().background(color, CircleShape))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySheet(currencies: List<Currency>, picked: List<Long>, onPick: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val icons by JobIcons.currencies.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Currencies", fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text)
            Text("Pick up to $MAX_CURRENCIES", color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                currencies.forEach { currency ->
                    val on = currency.id in picked
                    val enabled = on || picked.size < MAX_CURRENCIES
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled, role = Role.Checkbox) { onPick(if (on) picked - currency.id else picked + currency.id) }
                            .padding(vertical = 4.dp),
                    ) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            icons[currency.id.toString()]?.let { Image(it, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize()) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).alpha(if (enabled) 1f else EMPTY_ALPHA)) {
                            Text(currency.name, color = AppColors.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(NumberFormat.getIntegerInstance().format(currency.count), color = AppColors.muted, fontSize = 12.sp)
                        }
                        Checkbox(
                            checked = on,
                            onCheckedChange = null,
                            enabled = enabled,
                            colors = CheckboxDefaults.colors(checkedColor = AppColors.accent, checkmarkColor = AppColors.onAccent, uncheckedColor = AppColors.muted),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Line(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppColors.line))
}

@Composable
private fun Glyph(icon: ImageBitmap, color: Color, modifier: Modifier) {
    Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(color), filterQuality = FilterQuality.High, modifier = modifier)
}

@Composable
internal fun animated(target: Float, duration: Int): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target, tween(duration, easing = FastOutSlowInEasing)) }
    return value.value
}
