package com.benji.partybell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channel = intent.getStringExtra(Notifier.EXTRA_CHANNEL) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString() ?: return
        val message = PartyState.clean(text)
        if (message.isEmpty()) return
        PartyState.send(channel, message)
        val failed = PartyState.outbox.value.lastOrNull()?.takeIf { it.state == SendState.Failed }
        Notifier(context, AppSettings(context)).replied(channel, if (failed == null) message else "$message (not sent: ${failed.reason})")
    }
}
