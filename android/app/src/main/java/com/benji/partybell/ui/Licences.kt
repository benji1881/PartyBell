package com.benji.partybell.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

private class Licence(val name: String, val covers: String, val files: List<String> = emptyList(), val url: String = "")

private val licences = listOf(
    Licence(
        "Apache License 2.0",
        "AndroidX, Jetpack Compose, Kotlin, kotlinx, OkHttp, Okio, Guava, Firebase components, JSpecify, javax.inject",
        files = listOf("Apache-2.0.txt"),
    ),
    Licence("SIL Open Font License 1.1", "Fraunces, Plus Jakarta Sans", files = listOf("Fraunces-OFL.txt", "PlusJakartaSans-OFL.txt")),
    Licence("Google Play services", "Code scanner and ML Kit", url = "https://developers.google.com/ml-kit/terms"),
)

@Composable
fun LicencesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<Licence?>(null) }
    val open = reading

    BackHandler { if (reading != null) reading = null else onBack() }

    key(open) {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (open != null) {
                ScreenHeader(open.name) { reading = null }
                val text = remember(open) {
                    open.files.joinToString("\n\n") { file -> context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }
                }
                Card { Text(text, color = AppColors.text, fontSize = 12.sp) }
            } else {
                ScreenHeader("Open source licences", onBack)
                licences.forEach { licence ->
                    Card {
                        Column {
                            Text(licence.name, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(licence.covers, color = AppColors.muted, fontSize = 13.sp)
                        }
                        Divider()
                        if (licence.url.isEmpty()) {
                            NavRow("Read licence", "") { reading = licence }
                        } else {
                            NavRow("Read terms", "") { context.startActivity(Intent(Intent.ACTION_VIEW, licence.url.toUri())) }
                        }
                    }
                }
            }
        }
    }
}
