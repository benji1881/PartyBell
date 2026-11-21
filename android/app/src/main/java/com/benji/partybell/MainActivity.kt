package com.benji.partybell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.benji.partybell.ui.App
import com.benji.partybell.ui.PartyBellTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val request = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotifications()

        JobIcons.load(this)
        ChatPrefs.load(this)
        TellHistory.load(this)
        request.value = intent.getStringExtra(Notifier.EXTRA_OPEN)
        val settings = AppSettings(this)
        PartyService.startIfEnabled(this)

        setContent {
            PartyBellTheme {
                App(settings, request.value, onRequestHandled = { request.value = null })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Updater.check(this)
        PartyService.nudgeIfEnabled(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_OPEN)?.let { request.value = it }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
