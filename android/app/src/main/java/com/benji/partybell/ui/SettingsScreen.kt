package com.benji.partybell.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benji.partybell.AppSettings
import com.benji.partybell.NotifyKind
import com.benji.partybell.PairingScanner
import com.benji.partybell.PartyService
import com.benji.partybell.ScanResult
import com.benji.partybell.Updater

@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var paired by remember { mutableStateOf(settings.isPaired) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val update by Updater.update.collectAsState()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppColors.card)
                    .border(1.dp, AppColors.line, CircleShape)
                    .clickable(onClick = onBack),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("Settings", fontFamily = Fraunces, fontSize = 26.sp, color = AppColors.text)
        }

        update?.let { available ->
            Card("Updates") {
                Column {
                    Text("${available.version} is available.", color = AppColors.text)
                    Text("You have ${Updater.current(context)}", color = AppColors.muted, fontSize = 13.sp)
                }
                Button(onClick = { Updater.download(context) }) { Text("Download update") }
            }
        }

        Card("Pairing") {
            if (paired) {
                Text("Paired with your PC.", color = AppColors.text)
                Text("${settings.hosts.joinToString(", ")} · port ${settings.port}", color = AppColors.muted, fontSize = 13.sp)
            } else {
                Text("Type /partybell in game, press Pair a phone and scan the code.", color = AppColors.muted)
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
            scanError?.let { Text(it, color = AppColors.red) }
        }

        if (paired) {
            Card("Connection") {
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
        }

        Card("Notifications") {
            NotifyKind.entries.forEach { kind -> NotifyRow(settings, kind) }
        }

        Card("Battery") {
            Text("Turn off battery optimisation for PartyBell so it stays connected while the screen is off.", color = AppColors.muted)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("Battery settings") }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(18.dp))
            .border(1.dp, AppColors.line, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(title, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        content()
    }
}

@Composable
private fun NotifyRow(settings: AppSettings, kind: NotifyKind) {
    var on by remember { mutableStateOf(settings.notifies(kind)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(kind.label, color = AppColors.text, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = {
            on = it
            settings.setNotifies(kind, it)
        })
    }
}
