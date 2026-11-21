package com.benji.partybell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DutyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(Notifier.EXTRA_DUTY) ?: return
        val notifier = Notifier(context, AppSettings(context))
        if (PartyState.duty(action)) notifier.dutying(action) else notifier.dutied(DutyReply(action, false, Reasons.OFFLINE))
    }
}
