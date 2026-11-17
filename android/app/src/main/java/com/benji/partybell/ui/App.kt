package com.benji.partybell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.AppSettings
import com.benji.partybell.PartyState
import com.benji.partybell.Status

@Composable
fun App(settings: AppSettings) {
    var showSettings by rememberSaveable { mutableStateOf(!settings.isPaired) }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(settings, onBack = { showSettings = false }, modifier = Modifier.safeDrawingPadding())
        } else {
            PartyScreen(onSettings = { showSettings = true })
        }
    }
}

@Composable
private fun PartyScreen(onSettings: () -> Unit) {
    val party by PartyState.party.collectAsStateWithLifecycle()
    val feed by PartyState.feed.collectAsStateWithLifecycle()
    val status by PartyState.status.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("PartyBell", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            StatusLabel(status)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        PartyPanel(party)
        Feed(feed, Modifier.weight(1f))
    }
}

@Composable
private fun StatusLabel(status: Status) {
    val color = when (status) {
        Status.Connected -> Color(0xFF6FAE7F)
        Status.Connecting -> Color(0xFFE39A55)
        Status.Off, Status.NotPaired -> Color(0xFFE06A70)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(status.label, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}
