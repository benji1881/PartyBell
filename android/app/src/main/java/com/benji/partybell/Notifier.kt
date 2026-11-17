package com.benji.partybell

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

class Notifier(private val context: Context, private val settings: AppSettings) {
    companion object {
        const val SERVICE_ID = 1
        const val KEY_REPLY = "reply"
        const val EXTRA_CHANNEL = "channel"
        private const val PARTY_ID = 2
        private const val ALLIANCE_ID = 3
        private const val FIRST_EVENT_ID = 10
        private const val MAX_LINES = 8
        private const val CHANNEL_PARTY = "party"
        private const val CHANNEL_SERVICE = "service"
        private val conversations = mutableMapOf<String, MutableList<NotificationCompat.MessagingStyle.Message>>()
        private var nextId = FIRST_EVENT_ID
    }

    private val manager = NotificationManagerCompat.from(context)
    private val you = Person.Builder().setName("You").build()

    init {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_PARTY, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Party updates")
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_SERVICE, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Connection")
                .build()
        )
    }

    fun serviceNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("PartyBell")
            .setContentText("Listening for party updates")
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()

    fun notify(item: FeedItem) {
        when (item) {
            is PartyEventItem -> event(item)
            is ChatItem -> if (!item.self && settings.notifies(NotifyKind.Chat)) chat(item.channel, item.name, item.text)
            is OutgoingItem -> Unit
        }
    }

    fun replied(channel: String, text: String) {
        if (channel in conversations) chat(channel, null, text)
    }

    fun clearChat() {
        conversations.clear()
        manager.cancel(PARTY_ID)
        manager.cancel(ALLIANCE_ID)
    }

    @SuppressLint("MissingPermission")
    private fun chat(channel: String, name: String?, text: String) {
        val lines = conversations.getOrPut(channel) { mutableListOf() }
        val sender = name?.let { Person.Builder().setName(it).build() }
        lines += NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), sender)
        while (lines.size > MAX_LINES) lines.removeAt(0)
        if (!canPost()) return

        val style = NotificationCompat.MessagingStyle(you)
            .setConversationTitle(if (channel == ChatChannel.ALLIANCE) "Alliance" else "Party")
            .setGroupConversation(true)
        lines.forEach(style::addMessage)

        val notification = NotificationCompat.Builder(context, CHANNEL_PARTY)
            .setSmallIcon(R.drawable.ic_bell)
            .setStyle(style)
            .setContentIntent(openApp())
            .addAction(replyAction(channel))
            .setOnlyAlertOnce(name == null)
            .setAutoCancel(true)
            .build()
        manager.notify(if (channel == ChatChannel.ALLIANCE) ALLIANCE_ID else PARTY_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun event(item: PartyEventItem) {
        if (!settings.notifies(item.kind.notify) || !canPost()) return
        val (title, text) = when (item.kind) {
            EventKind.Joined, EventKind.Left -> item.name to item.text
            EventKind.Full -> item.text to PartyState.party.value.let { "${it.members.size}/${it.capacity}" }
            EventKind.Disbanded -> item.text to ""
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PARTY)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(nextId++, notification)
    }

    private fun replyAction(channel: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
        val intent = Intent(context, ReplyReceiver::class.java).putExtra(EXTRA_CHANNEL, channel)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, channel.hashCode(), intent, flags)
        return NotificationCompat.Action.Builder(R.drawable.ic_bell, "Reply", pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun canPost() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
