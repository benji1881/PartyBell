package com.benji.partybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val RING_RATIO = 0.056f
private const val TEXT_RATIO = 0.3f

@Composable
fun JobAvatar(job: String, role: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(3.dp, CircleShape)
            .background(AppColors.Ring, CircleShape)
            .padding(size * RING_RATIO)
            .clip(CircleShape)
            .background(roleColor(role)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = job.ifEmpty { "?" },
            color = AppColors.Text,
            fontSize = (size.value * TEXT_RATIO).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
