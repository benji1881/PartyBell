package com.benji.partybell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Connects again after the phone restarts or the app updates, like it would after opening the app.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) PartyService.startIfEnabled(context)
    }
}
