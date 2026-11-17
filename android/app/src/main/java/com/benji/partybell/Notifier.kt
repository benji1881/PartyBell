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
        const val EXTRA_CONVERSATION = "conversation"
        const val EXTRA_OPEN = "open"
        const val EXTRA_READY = "ready"
        private const val CHAT_ID = 2
        private const val READY_ID = 4
        private const val QUEUE_ID = 5
        private const val READY_TIMEOUT_MS = 35_000L
        private const val QUEUE_TIMEOUT_MS = 45_000L
        private const val RESULT_TIMEOUT_MS = 60_000L
        private const val FIRST_EVENT_ID = 10
        private const val MAX_LINES = 8
        private const val CHANNEL_PARTY = "party"
        private const val CHANNEL_SERVICE = "connection"
        private const val OLD_CHANNEL_SERVICE = "service"
        private const val CHANNEL_ALERTS = "alerts"
        private const val CHANNEL_MESSAGES = "messages"
        private val conversations = mutableMapOf<String, MutableList<NotificationCompat.MessagingStyle.Message>>()
        private var nextId = FIRST_EVENT_ID
    }

    private val manager = NotificationManagerCompat.from(context)
    private val you = Person.Builder().setName("You").build()

    init {
        ChatPrefs.load(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_PARTY, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Party updates")
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Ready checks and duty pops")
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_MESSAGES, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Tells and Free Company")
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_SERVICE, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName("Connection")
                .setShowBadge(false)
                .build()
        )
        manager.deleteNotificationChannel(OLD_CHANNEL_SERVICE)
    }

    fun serviceNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("PartyBell")
            .setContentText("Listening for party updates")
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    fun notify(item: FeedItem) {
        when (item) {
            is PartyEventItem -> event(item)
            is ChatItem -> if (!item.self && settings.notifies(kindOf(item.channel)) && muteKey(item.conversation) !in ChatPrefs.muted.value) {
                chat(item.conversation, item.name, item.text)
            }
            is OutgoingItem -> Unit
        }
    }

    fun replied(conversation: String, text: String) {
        if (conversation in conversations) chat(conversation, null, text)
    }

    fun answering(ready: Boolean) = readyStatus(if (ready) "Sending ready…" else "Sending not ready…")

    fun answered(answer: Answered) = readyStatus(
        when {
            answer.ok -> if (answer.ready) "You're ready" else "You're not ready"
            else -> "Couldn't answer: " + when (answer.reason) {
                "disabled" -> "answering from phones is off in the plugin"
                "no ready check" -> "the ready check is over"
                "answered" -> "already answered in game"
                "no prompt" -> "the ready check window isn't open"
                "offline" -> "not connected"
                else -> "the game refused it"
            }
        }
    )

    fun dismiss(kind: String) {
        if (kind == EventKind.Queue.key) manager.cancel(QUEUE_ID)
    }

    fun clearChat() {
        listOf(ChatChannel.PARTY, ChatChannel.ALLIANCE).forEach {
            conversations.remove(it)
            manager.cancel(it, CHAT_ID)
        }
    }

    fun opened(conversation: String) {
        conversations.remove(conversation)
        manager.cancel(conversation, CHAT_ID)
    }

    private fun muteKey(conversation: String) = if (ChatChannel.isParty(conversation)) ChatChannel.PARTY else conversation

    private fun kindOf(channel: String) = when (channel) {
        ChatChannel.TELL -> NotifyKind.Tells
        ChatChannel.FC -> NotifyKind.FreeCompany
        else -> NotifyKind.Chat
    }

    @SuppressLint("MissingPermission")
    private fun chat(conversation: String, name: String?, text: String) {
        val lines = conversations.getOrPut(conversation) { mutableListOf() }
        val sender = name?.let { Person.Builder().setName(it).build() }
        lines += NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), sender)
        while (lines.size > MAX_LINES) lines.removeAt(0)
        if (!canPost()) return

        val group = ChatChannel.channelOf(conversation) != ChatChannel.TELL
        val title = ChatChannel.title(conversation)
        val style = NotificationCompat.MessagingStyle(you)
            .setConversationTitle(if (group) title else null)
            .setGroupConversation(group)
        lines.forEach(style::addMessage)

        val hidden = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("PartyBell")
            .setContentText("New message")
            .build()
        val notification = NotificationCompat.Builder(context, if (ChatChannel.isParty(conversation)) CHANNEL_PARTY else CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_bell)
            .setStyle(style)
            .setContentIntent(openApp(conversation))
            .addAction(replyAction(conversation))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .setOnlyAlertOnce(name == null)
            .setAutoCancel(true)
            .build()
        manager.notify(conversation, CHAT_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun event(item: PartyEventItem) {
        if (item.kind == EventKind.ReadyCheckDone) manager.cancel(READY_ID)
        if (!settings.notifies(item.kind.notify) || !canPost()) return
        when (item.kind) {
            EventKind.ReadyCheck -> return readyCheck(item.answerable)
            EventKind.ReadyCheckDone -> return alert(READY_ID, "Ready check", item.text, RESULT_TIMEOUT_MS)
            EventKind.Queue -> return alert(QUEUE_ID, "Duty ready", item.detail, QUEUE_TIMEOUT_MS)
            else -> Unit
        }
        val (title, text) = when (item.kind) {
            EventKind.Full -> item.text to PartyState.party.value.let { "${it.members.size}/${it.capacity}" }
            EventKind.Disbanded -> item.text to ""
            else -> item.name to item.text
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

    @SuppressLint("MissingPermission")
    private fun readyCheck(answerable: Boolean) {
        val builder = alertBuilder("Ready check", if (answerable) "Are you ready?" else "Waiting for the party", READY_TIMEOUT_MS)
        if (answerable) {
            builder.addAction(answerAction(true, "Ready"))
            builder.addAction(answerAction(false, "Not ready"))
        }
        manager.notify(READY_ID, builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun readyStatus(text: String) {
        if (!settings.notifies(NotifyKind.ReadyCheck) || !canPost()) return
        manager.notify(READY_ID, alertBuilder("Ready check", text, READY_TIMEOUT_MS).setOnlyAlertOnce(true).setSilent(true).build())
    }

    @SuppressLint("MissingPermission")
    private fun alert(id: Int, title: String, text: String, timeout: Long) =
        manager.notify(id, alertBuilder(title, text, timeout).build())

    private fun alertBuilder(title: String, text: String, timeout: Long) =
        NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setTimeoutAfter(timeout)
            .setAutoCancel(true)

    private fun answerAction(ready: Boolean, label: String): NotificationCompat.Action {
        val intent = Intent(context, AnswerReceiver::class.java).putExtra(EXTRA_READY, ready)
        val pending = PendingIntent.getBroadcast(
            context,
            if (ready) 1 else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_bell, label, pending).build()
    }

    private fun replyAction(conversation: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
        val intent = Intent(context, ReplyReceiver::class.java).putExtra(EXTRA_CONVERSATION, conversation)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, conversation.hashCode(), intent, flags)
        return NotificationCompat.Action.Builder(R.drawable.ic_bell, "Reply", pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun canPost() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(conversation: String? = null): PendingIntent = PendingIntent.getActivity(
        context,
        conversation?.hashCode() ?: 0,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, conversation),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
