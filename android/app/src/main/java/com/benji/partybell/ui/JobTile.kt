package com.benji.partybell.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.JobIcons

private const val CORNER_RATIO = 0.3f
private const val GLYPH_RATIO = 0.74f
private const val TEXT_RATIO = 0.3f
private const val INITIAL_RATIO = 0.42f
private const val OPEN_ALPHA = 0.6f

@Composable
fun JobTile(job: String, role: Int, name: String, size: Dp, modifier: Modifier = Modifier) {
    val icons by JobIcons.icons.collectAsStateWithLifecycle()
    val icon = icons[job]
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(size)
            .background(roleColor(if (job.isEmpty()) 0 else role), RoundedCornerShape(size * CORNER_RATIO)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Image(
                bitmap = icon,
                contentDescription = job,
                colorFilter = ColorFilter.tint(Cream),
                filterQuality = FilterQuality.High,
                modifier = Modifier.size(size * GLYPH_RATIO),
            )
            job.isNotEmpty() -> Text(
                text = job,
                color = Cream,
                fontWeight = FontWeight.ExtraBold,
                fontSize = with(density) { (size * TEXT_RATIO).toSp() },
                maxLines = 1,
            )
            else -> Text(
                text = name.take(1).uppercase(),
                color = Cream,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { (size * INITIAL_RATIO).toSp() },
                maxLines = 1,
            )
        }
    }
}

@Composable
fun RoleTile(role: Int, size: Dp, modifier: Modifier = Modifier) {
    val roles by JobIcons.roles.collectAsStateWithLifecycle()
    val name = roleName(role)
    val icon = roles[name] ?: return EmptyTile(size, modifier)
    Image(
        bitmap = icon,
        contentDescription = "Open $name slot",
        filterQuality = FilterQuality.High,
        modifier = modifier.size(size).alpha(OPEN_ALPHA),
    )
}

@Composable
fun EmptyTile(size: Dp, modifier: Modifier = Modifier) {
    val color = AppColors.muted.copy(alpha = 0.5f)
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.05f
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = this.size.copy(this.size.width - stroke, this.size.height - stroke),
            cornerRadius = CornerRadius(this.size.minDimension * CORNER_RATIO),
            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2.4f, stroke * 2f))),
        )
    }
}

@Composable
fun Crown(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.78f)
            lineTo(w * 0.04f, h * 0.28f)
            lineTo(w * 0.32f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.16f)
            lineTo(w * 0.68f, h * 0.5f)
            lineTo(w * 0.96f, h * 0.28f)
            lineTo(w * 0.9f, h * 0.78f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun Clock(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.12f
            val center = this.center
            val radius = this.size.minDimension / 2 - stroke
            drawCircle(color, radius, center, style = Stroke(stroke))
            drawLine(color, center, Offset(center.x, center.y - radius * 0.6f), stroke, StrokeCap.Round)
            drawLine(color, center, Offset(center.x + radius * 0.45f, center.y), stroke, StrokeCap.Round)
        }
    }
}
