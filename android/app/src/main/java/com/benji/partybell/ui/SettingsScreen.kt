package com.benji.partybell.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.benji.partybell.AppSettings
import com.benji.partybell.NotifyKind
import com.benji.partybell.PairingScanner
import com.benji.partybell.PartyService
import com.benji.partybell.ScanResult

private val ErrorColor = Color(0xFFE06A70)

@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var paired by remember { mutableStateOf(settings.isPaired) }
    var scanError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        Text("Pairing", style = MaterialTheme.typography.titleMedium)
        if (paired) {
            Text("Paired with your PC.")
            Text("${settings.hosts.joinToString(", ")} · port ${settings.port}", color = AppColors.Muted)
        } else {
            Text("Type /partybell in game, press Pair a phone and scan the code.", color = AppColors.Muted)
        }
        Button(onClick = {
            scanError = null
            PairingScanner.scan(context) { result ->
                when (result) {
                    is ScanResult.Paired -> {
                        settings.pair(result.pairing)
                        paired = true
                        PartyService.start(context)
                        onBack()
                    }
                    ScanResult.NotPartyBell -> scanError = "That isn't a PartyBell pairing code."
                    ScanResult.Failed -> scanError = "Couldn't open the scanner."
                }
            }
        }) { Text(if (paired) "Scan a new pairing code" else "Scan pairing code") }
        scanError?.let { Text(it, color = ErrorColor) }

        if (paired) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    settings.enabled = true
                    PartyService.start(context)
                    onBack()
                }) { Text("Connect") }
                OutlinedButton(onClick = {
                    settings.enabled = false
                    PartyService.stop(context)
                }) { Text("Disconnect") }
            }
        }

        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        NotifyKind.entries.forEach { kind -> NotifyRow(settings, kind) }

        Text("Battery", style = MaterialTheme.typography.titleMedium)
        Text("Turn off battery optimisation for PartyBell so it stays connected while the screen is off.", color = AppColors.Muted)
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }) { Text("Battery settings") }
    }
}

@Composable
private fun NotifyRow(settings: AppSettings, kind: NotifyKind) {
    var on by remember { mutableStateOf(settings.notifies(kind)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(kind.label, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = {
            on = it
            settings.setNotifies(kind, it)
        })
    }
}
