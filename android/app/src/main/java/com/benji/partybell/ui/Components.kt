package com.benji.partybell.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.AppSettings
import com.benji.partybell.ChatChannel
import com.benji.partybell.ChatItem
import com.benji.partybell.ChatPrefs
import com.benji.partybell.FeedItem
import com.benji.partybell.FinderCategory
import com.benji.partybell.Job
import com.benji.partybell.JobIcons
import com.benji.partybell.Link
import com.benji.partybell.Listing
import com.benji.partybell.Listings
import com.benji.partybell.Notifier
import com.benji.partybell.PartyEventItem
import com.benji.partybell.PartyService
import com.benji.partybell.PartyState
import com.benji.partybell.Status
import kotlinx.coroutines.delay

// Small building blocks shared by several screens.

private val OpenSlot = Color(0xFFB9B4A8)

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    Text(
        if (count > 99) "99+" else count.toString(),
        color = AppColors.onAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(AppColors.accent, RoundedCornerShape(50))
            .widthIn(min = 18.dp)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun Empty(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = AppColors.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

/** One-line screen title with what is going on next to it, and actions on the right. */
@Composable
fun Header(title: String, onSettings: () -> Unit, onRefresh: (() -> Unit)? = null, detail: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Text(title, fontFamily = Fraunces, fontSize = 24.sp, color = AppColors.text)
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(top = 3.dp)) { detail() }
        if (onRefresh != null) CircleButton(Icons.Filled.Refresh, "Refresh", onRefresh)
        CircleButton(Icons.Filled.Settings, "Settings", onSettings)
    }
}

@Composable
fun StatusLine(status: Status) {
    Dot(statusColor(status))
    Spacer(Modifier.width(6.dp))
    Text(status.label, color = AppColors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun SectionLabel(title: String) {
    Text(
        title.uppercase(),
        color = AppColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
    )
}

/** A row of a grouped list: the rows share one card and are split by hairlines that start at [inset]. */
@Composable
fun Modifier.groupRow(index: Int, count: Int, inset: Dp = 0.dp): Modifier {
    val top = if (index == 0) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    val line = AppColors.line
    return clip(RoundedCornerShape(top, top, bottom, bottom))
        .background(AppColors.card)
        .drawWithContent {
            drawContent()
            if (index > 0) drawLine(line, Offset(inset.toPx(), 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
}

@Composable
fun TitleBar(title: String, onBack: () -> Unit, subtitle: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.offset(x = (-8).dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Fraunces, fontSize = 22.sp, color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) { subtitle() }
        }
    }
}

@Composable
fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = AppColors.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

@Composable
internal fun statusColor(status: Status): Color = when (status) {
    Status.Connected -> AppColors.accent
    Status.Connecting -> AppColors.yellow
    Status.Off -> AppColors.muted
    Status.NotPaired, Status.UpdatePlugin, Status.UpdateApp -> AppColors.red
}

@Composable
internal fun NavRow(label: String, value: String, modifier: Modifier = Modifier, inset: Dp = 0.dp, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = inset, vertical = 14.dp),
    ) {
        Text(label, color = AppColors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = AppColors.muted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.line))
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

private val FadeWidth = 24.dp
private val GroupRadius = 18.dp

/** Fades content out at the edges that can still scroll, so it is clear there is more. */
fun Modifier.fadeEdges(start: Boolean, end: Boolean, vertical: Boolean = false) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FadeWidth.toPx()
        val length = if (vertical) size.height else size.width
        fun edge(from: Float, to: Float) = drawRect(
            if (vertical) Brush.verticalGradient(listOf(Color.Transparent, Color.Black), from, to) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), from, to),
            blendMode = BlendMode.DstIn,
        )
        if (start) edge(0f, fade)
        if (end) edge(length, length - fade)
    }
