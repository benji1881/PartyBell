package com.benji.partybell

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.benji.partybell.ui.roleColor
import com.benji.partybell.ui.roleRank

class Notifier(private val context: Context, private val settings: AppSettings) {
    companion object {
        const val SERVICE_ID = 1
        const val KEY_REPLY = "reply"
        const val EXTRA_CONVERSATION = "conversation"
        const val EXTRA_OPEN = "open"
        const val EXTRA_READY = "ready"
        const val EXTRA_DUTY = "duty"
        const val OPEN_SETTINGS = "settings"
        private const val CHAT_ID = 2
        private const val READY_ID = 4
        private const val QUEUE_ID = 5
        private const val LOST_ID = 6
        private const val UPDATE_ID = 7
        private const val READY_TIMEOUT_MS = 35_000L
        private const val QUEUE_TIMEOUT_MS = 45_000L
        private const val RESULT_TIMEOUT_MS = 60_000L
        private const val FIRST_EVENT_ID = 10
        private const val MAX_LINES = 8
        private const val BURST_MS = 10_000L
        private const val BURST_NAMES = 3
        private const val CHANNEL_PARTY = "party"
        private const val CHANNEL_SERVICE = "connection"
        private const val OLD_CHANNEL_SERVICE = "service"
        private const val CHANNEL_ALERTS = "alerts"
        private const val CHANNEL_MESSAGES = "messages"
        private const val CHANNEL_LOST = "lost"
        private const val CHANNEL_UPDATES = "updates"
        private const val BAR_WIDTH_DP = 4
        private const val BAR_GAP_DP = 3
        private const val BAR_HEIGHT_DP = 14
        private const val OPEN_SLOT = 0xFFB9B4A8.toInt()
        private val conversations = mutableMapOf<String, MutableList<NotificationCompat.MessagingStyle.Message>>()
        private var nextId = FIRST_EVENT_ID
        private var burst: Burst? = null
        private val GROUPED = setOf(EventKind.Joined, EventKind.Left)
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
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_LOST, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Lost connection")
                .setShowBadge(false)
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_UPDATES, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("App updates")
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

    fun dutying(action: String) = queueStatus(if (action == DutyAction.COMMENCE) "Commencing…" else "Withdrawing…")

    fun dutied(reply: DutyReply) {
        if (!reply.ok) queueStatus("Couldn't answer: " + PartyState.dutyReason(reply.reason).replaceFirstChar(Char::lowercase))
    }

    fun dismiss(kind: String) = when (kind) {
        EventKind.Queue.key -> manager.cancel(QUEUE_ID)
        EventKind.ReadyCheck.key -> manager.cancel(READY_ID)
        else -> Unit
    }

    @SuppressLint("MissingPermission")
    fun lost() {
        if (!settings.notifies(NotifyKind.Disconnected) || !canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_LOST)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Lost connection to your PC")
            .setContentIntent(openApp())
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        manager.notify(LOST_ID, notification)
    }

    fun found() = manager.cancel(LOST_ID)

    @SuppressLint("MissingPermission")
    fun update(version: String) {
        if (!settings.notifies(NotifyKind.Update) || !canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("PartyBell $version is available")
            .setContentIntent(openApp(OPEN_SETTINGS))
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(UPDATE_ID, notification)
    }

    fun updated() = manager.cancel(UPDATE_ID)

    fun clearChat(all: Boolean) {
        val party = listOf(ChatChannel.PARTY, ChatChannel.ALLIANCE)
        (if (all) (conversations.keys + party).toList() else party).forEach {
            conversations.remove(it)
            manager.cancel(it, CHAT_ID)
        }
    }

    fun opened(conversation: String) {
        conversations.remove(conversation)
        manager.cancel(conversation, CHAT_ID)
        if (ChatChannel.isParty(conversation)) clearEvents()
    }

    private fun clearEvents() {
        burst = null
        manager.activeNotifications
            .filter { it.tag == null && it.id >= FIRST_EVENT_ID }
            .forEach { manager.cancel(it.id) }
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
            EventKind.Queue -> return queue(item.detail)
            else -> Unit
        }
        val now = System.currentTimeMillis()
        val group = burst?.takeIf { item.kind in GROUPED && it.kind == item.kind && now - it.time < BURST_MS }
        val names = group?.names.orEmpty() + item.name
        val id = group?.id ?: nextId++
        if (item.kind in GROUPED) burst = Burst(item.kind, id, names, now)
        val (title, text) = when {
            item.kind == EventKind.Full || item.kind == EventKind.Disbanded -> item.text to ""
            names.size > 1 -> "${names.size} ${verb(item.kind)}" to listNames(names, BURST_NAMES)
            else -> item.name to item.text
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PARTY)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        if (item.kind == EventKind.Full) {
            val view = RemoteViews(context.packageName, R.layout.notification_party_full)
            view.setTextViewText(R.id.title, title)
            view.setImageViewBitmap(R.id.bars, roleBars(PartyState.party.value))
            builder.setCustomContentView(view).setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }
        manager.notify(id, builder.build())
    }

    private fun roleBars(party: Party): Bitmap {
        val own = party.members.groupBy { it.group }.values.firstOrNull().orEmpty().sortedBy { roleRank(it.role) }
        val slots = minOf(party.capacity, Party.GROUP_SIZE)
        val colors = own.take(slots).map { roleColor(it.role).toArgb() } + List(slots - own.size.coerceAtMost(slots)) { OPEN_SLOT }
        val density = context.resources.displayMetrics.density
        val width = BAR_WIDTH_DP * density
        val gap = BAR_GAP_DP * density
        val height = BAR_HEIGHT_DP * density
        val radius = width / 2
        val bitmap = createBitmap((colors.size * (width + gap) - gap).toInt().coerceAtLeast(1), height.toInt())
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        colors.forEachIndexed { index, color ->
            val left = index * (width + gap)
            paint.color = color
            canvas.drawRoundRect(left, 0f, left + width, height, radius, radius, paint)
        }
        return bitmap
    }

    @SuppressLint("MissingPermission")
    private fun queue(duty: String) {
        val builder = queueBuilder(duty)
        if (PartyState.queue.value?.actions == true) {
            builder.addAction(dutyAction(DutyAction.COMMENCE, "Commence"))
            builder.addAction(dutyAction(DutyAction.WITHDRAW, "Withdraw"))
        }
        manager.notify(QUEUE_ID, builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun queueStatus(text: String) {
        if (!settings.notifies(NotifyKind.Queue) || !canPost()) return
        manager.notify(QUEUE_ID, queueBuilder(text).setOnlyAlertOnce(true).setSilent(true).build())
    }

    private fun queueBuilder(text: String): NotificationCompat.Builder {
        val now = System.currentTimeMillis()
        val expires = PartyState.queue.value?.expires?.takeIf { it > now } ?: (now + QUEUE_TIMEOUT_MS)
        return alertBuilder("Duty ready", text, expires - now)
            .setWhen(expires)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
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
        return NotificationCompat.Action.Builder(R.drawable.ic_bell, label, pending)
            .setAuthenticationRequired(true)
            .build()
    }

    private fun dutyAction(action: String, label: String): NotificationCompat.Action {
        val intent = Intent(context, DutyReceiver::class.java).putExtra(EXTRA_DUTY, action)
        val pending = PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_bell, label, pending)
            .setAuthenticationRequired(true)
            .build()
    }

    private fun replyAction(conversation: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").setChoices(QUICK_REPLIES.toTypedArray<CharSequence>()).build()
        val intent = Intent(context, ReplyReceiver::class.java).putExtra(EXTRA_CONVERSATION, conversation)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, conversation.hashCode(), intent, flags)
        return NotificationCompat.Action.Builder(R.drawable.ic_bell, "Reply", pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAuthenticationRequired(true)
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

    private data class Burst(val kind: EventKind, val id: Int, val names: List<String>, val time: Long)
}
