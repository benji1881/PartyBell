package com.benji.partybell.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

private const val WIDE_DP = 600

data class Tabletop(val top: Int, val bottom: Int)

@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_DP

@Composable
fun rememberTabletop(): Tabletop? {
    val activity = LocalActivity.current ?: return null
    val hinge = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>()
                .firstOrNull { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
                ?.let { Tabletop(it.bounds.top, it.bounds.bottom) }
        }
    }
    val tabletop by hinge.collectAsStateWithLifecycle(null)
    return tabletop
}
