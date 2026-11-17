package com.benji.partybell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversation = intent.getStringExtra(Notifier.EXTRA_CONVERSATION) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString() ?: return
        val message = PartyState.clean(text)
        if (message.isEmpty()) return
        PartyState.send(ChatChannel.channelOf(conversation), message, ChatChannel.peerOf(conversation))
        val failed = PartyState.outbox.value.lastOrNull()?.takeIf { it.state == SendState.Failed }
        Notifier(context, AppSettings(context)).replied(conversation, if (failed == null) message else "$message (not sent: ${failed.reason})")
    }
}
