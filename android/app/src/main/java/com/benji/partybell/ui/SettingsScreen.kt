package com.benji.partybell.ui

import android.content.Context
import android.content.Intent
import android.os.PowerManager
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benji.partybell.AppSettings
import com.benji.partybell.NotifyKind
import com.benji.partybell.PairingScanner
import com.benji.partybell.PartyService
import com.benji.partybell.PartyState
import com.benji.partybell.ScanResult
import com.benji.partybell.Updater

private val notifyGroups = listOf(
    "Party" to listOf(NotifyKind.Full, NotifyKind.ReadyCheck, NotifyKind.Queue, NotifyKind.Joined, NotifyKind.Left, NotifyKind.Disbanded),
    "Chat" to listOf(NotifyKind.Tells, NotifyKind.Chat, NotifyKind.FreeCompany),
    "App" to listOf(NotifyKind.Disconnected, NotifyKind.Update),
)

@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var paired by remember { mutableStateOf(settings.isPaired) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var batteryFree by remember { mutableStateOf(true) }
    val update by Updater.update.collectAsState()
    val status by PartyState.status.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        batteryFree = ignoresBattery(context)
        onPauseOrDispose { }
    }

    val scan = {
        scanError = null
        PairingScanner.scan(context) { result ->
            when (result) {
                is ScanResult.Paired -> {
                    settings.pair(result.pairing)
                    paired = true
                    enabled = true
                    PartyService.start(context)
                    onBack()
                }
                ScanResult.NotPartyBell -> scanError = "That isn't a PartyBell pairing code."
                ScanResult.Failed -> scanError = "Couldn't open the scanner."
            }
        }
    }

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

        if (!batteryFree) {
            BatteryWarning {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        Card {
            if (paired) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Dot(statusColor(status))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status.label, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${settings.hosts.joinToString(", ")} · port ${settings.port}", color = AppColors.muted, fontSize = 13.sp)
                    }
                    AppSwitch(checked = enabled, onCheckedChange = {
                        enabled = it
                        settings.enabled = it
                        if (it) PartyService.start(context) else PartyService.stop(context)
                    })
                }
                Divider()
                NavRow("Pair a new PC", "", onClick = scan)
            } else {
                Text("Not paired", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Type /partybell in game, press Pair a phone and scan the code.", color = AppColors.muted)
                Button(onClick = scan) { Text("Scan pairing code") }
            }
            scanError?.let { Text(it, color = AppColors.red) }
        }

        update?.let { available ->
            Card {
                Text("Updates", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Column {
                    Text("${available.version} is available.", color = AppColors.text)
                    Text("You have ${Updater.current(context)}", color = AppColors.muted, fontSize = 13.sp)
                }
                Button(onClick = { Updater.download(context) }) { Text("Download update") }
            }
        }

        Text("Notifications", fontFamily = Fraunces, fontSize = 20.sp, color = AppColors.text, modifier = Modifier.padding(top = 8.dp, start = 2.dp))

        notifyGroups.forEach { (label, kinds) ->
            Card {
                Text(label, color = AppColors.muted, fontSize = 13.sp)
                kinds.forEach { kind -> NotifyRow(settings, kind) }
            }
        }

        Card {
            NavRow("Sound and vibration", "") {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }

        Text(
            "PartyBell ${Updater.current(context)} · by benji1881",
            color = AppColors.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

private fun ignoresBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@Composable
private fun BatteryWarning(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.yellow.copy(alpha = 0.12f))
            .border(1.dp, AppColors.yellow.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.yellow, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("May disconnect with the screen off", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Turn off battery optimisation", color = AppColors.muted, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(18.dp))
            .border(1.dp, AppColors.line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun NotifyRow(settings: AppSettings, kind: NotifyKind) {
    var on by remember { mutableStateOf(settings.notifies(kind)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(kind.label, color = AppColors.text, modifier = Modifier.weight(1f))
        AppSwitch(checked = on, onCheckedChange = {
            on = it
            settings.setNotifies(kind, it)
        })
    }
}
