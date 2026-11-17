package com.benji.partybell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ready = intent.getBooleanExtra(Notifier.EXTRA_READY, false)
        val notifier = Notifier(context, AppSettings(context))
        if (PartyState.answer(ready)) notifier.answering(ready) else notifier.answered(Answered(ready, false, "offline"))
    }
}
