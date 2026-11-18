package com.benji.partybell.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

data class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private const val START_SCALE = 0.6f
private const val OPEN_DAMPING = 0.72f
private const val OPEN_STIFFNESS = 420f
private const val CLOSE_MS = 150
private val Gap = 8.dp
private val MenuShape = RoundedCornerShape(14.dp)

@Composable
fun SpringMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem>) {
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(TransformOrigin(0f, 0f)) }
    val position = remember { MenuPosition { origin = it } }

    LaunchedEffect(expanded) {
        if (expanded) {
            shown = true
            progress.animateTo(1f, spring(dampingRatio = OPEN_DAMPING, stiffness = OPEN_STIFFNESS))
        } else {
            progress.animateTo(0f, tween(CLOSE_MS))
            shown = false
        }
    }
    if (!shown) return

    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .padding(Gap)
                .width(230.dp)
                .graphicsLayer {
                    val scale = START_SCALE + (1f - START_SCALE) * progress.value
                    scaleX = scale
                    scaleY = scale
                    alpha = progress.value.coerceIn(0f, 1f)
                    transformOrigin = origin
                }
                .shadow(16.dp, MenuShape)
                .background(AppColors.card, MenuShape)
                .border(1.dp, AppColors.line, MenuShape)
                .clip(MenuShape),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.line))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            item.onClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Text(item.label, color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(item.icon, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private class MenuPosition(private val onPlaced: (TransformOrigin) -> Unit) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val below = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val above = anchorBounds.top - popupContentSize.height >= 0
        val y = when {
            below -> anchorBounds.bottom
            above -> anchorBounds.top - popupContentSize.height
            else -> anchorBounds.bottom - popupContentSize.height
        }
        val x = anchorBounds.left
        onPlaced(TransformOrigin(0.15f, if (below || !above) 0f else 1f))
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
