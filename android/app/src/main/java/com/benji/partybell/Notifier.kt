package com.benji.partybell

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class Notifier(private val context: Context, private val settings: AppSettings) {
    companion object {
        const val SERVICE_ID = 1
        private const val CHANNEL_PARTY = "party"
        private const val CHANNEL_SERVICE = "service"
    }

    private val manager = NotificationManagerCompat.from(context)
    private var nextId = SERVICE_ID + 1

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
        val kind = when (item) {
            is PartyEventItem -> item.kind.notify
            is ChatItem -> NotifyKind.Chat
        }
        if (!settings.notifies(kind) || !canPost()) return

        val (title, text) = when (item) {
            is PartyEventItem -> eventTitle(item) to eventText(item)
            is ChatItem -> item.name to item.text
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

    private fun eventTitle(item: PartyEventItem) = when (item.kind) {
        EventKind.Joined, EventKind.Left -> item.name
        EventKind.Full, EventKind.Disbanded -> item.text
    }

    private fun eventText(item: PartyEventItem) = when (item.kind) {
        EventKind.Joined, EventKind.Left -> item.text
        EventKind.Full -> PartyState.party.value.let { "${it.members.size}/${it.capacity}" }
        EventKind.Disbanded -> ""
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
