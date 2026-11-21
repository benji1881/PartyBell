package com.benji.partybell

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.min

// wakeUp is fired by an alarm that still runs in Doze, to try again after the PC has been off for a while.
class Connection(private val context: Context, private val settings: AppSettings, private val notifier: Notifier, private val wakeUp: PendingIntent) {
    private companion object {
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L
        const val MAX_BACKOFF_STEPS = 5
        const val NORMAL_CLOSE = 1000
        const val POLICY_CLOSE = 1008
        const val NOT_PAIRED = 4001
        const val UPDATE_NEEDED = 4002
        const val LOST_DELAY_MS = 60_000L
        // Quick retries keep the phone awake, so they only cover short drops like a Wi-Fi blip.
        const val IDLE_AFTER_MS = 2 * 60_000L
        const val IDLE_DELAY_MS = 5 * 60_000L
        const val CONNECT_AWAKE_MS = 15_000L
        const val MESSAGE_AWAKE_MS = 1_000L
        const val TAG = "PartyBell:connection"
        val waiting = setOf(Status.NotPaired, Status.UpdatePlugin, Status.UpdateApp)
    }

    private val client = OkHttpClient.Builder()
        .dns(PcAddress)
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val wake = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        .apply { setReferenceCounted(false) }
    private val lost = Runnable { notifier.lost() }
    private val reconnect = Runnable { connect() }
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wake.acquire(CONNECT_AWAKE_MS)
            handler.post { moved(network) }
        }
    }
    private var socket: WebSocket? = null
    private var socketNetwork: Network? = null
    private var hostIndex = 0
    private var round = 0
    private var failingSince = 0L
    private var running = false
    private var listening = false
    private var secret: ByteArray? = null
    // Only tried, not saved, until a handshake through it succeeds, so a spoofed discovery reply is never kept.
    private var discovered: String? = null
    private var discovering = false

    private val targets: List<String>
        get() = listOfNotNull(discovered) + settings.hosts.filter { it != discovered }

    fun restart() {
        stop()
        running = true
        listening = runCatching { connectivity.registerDefaultNetworkCallback(network) }.isSuccess
        connect()
    }

    fun wake() {
        if (!running || socket != null) return
        wake.acquire(CONNECT_AWAKE_MS)
        connect()
    }

    fun nudge() {
        if (!running || socket != null) return
        handler.removeCallbacks(reconnect)
        alarms.cancel(wakeUp)
        round = 0
        failingSince = 0L
        if (PartyState.link.value != Link.Ok) PartyState.setLink(Link.Searching)
        connect()
    }

    fun stop() {
        running = false
        if (listening) runCatching { connectivity.unregisterNetworkCallback(network) }
        listening = false
        handler.removeCallbacksAndMessages(null)
        alarms.cancel(wakeUp)
        wake.release()
        socket?.close(NORMAL_CLOSE, null)
        socket = null
        secret = null
        discovered = null
        // Its result may have been dropped with the handler's messages.
        discovering = false
        round = 0
        failingSince = 0L
        PartyState.setLink(Link.Ok)
        notifier.found()
        PartyState.disconnected()
        PartyState.setStatus(Status.Off)
    }

    private fun connect() {
        val hosts = targets
        if (!running || hosts.isEmpty()) return
        val secret = secret ?: Pairing.secret(settings.key)?.takeIf { it.isNotEmpty() }?.also { secret = it }
        if (secret == null) {
            round++
            schedule(PartyState.status.value.takeIf { it in waiting } ?: Status.Connecting)
            return
        }

        val host = hosts[hostIndex % hosts.size]
        // Unencrypted traffic is only allowed to PcAddress names, so the PC is reached through one.
        val url = try {
            HttpUrl.Builder()
                .scheme("http")
                .host(PcAddress.name(host) ?: throw IllegalArgumentException(host))
                .port(settings.port)
                .build()
        } catch (_: IllegalArgumentException) {
            PartyState.setStatus(Status.NotPaired)
            return
        }

        if (PartyState.status.value !in waiting) PartyState.setStatus(Status.Connecting)
        socketNetwork = connectivity.activeNetwork
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener(host, settings.phoneId, secret))
    }

    private fun moved(to: Network) {
        val current = socket
        if (current != null && to != socketNetwork) current.cancel() else nudge()
    }

    private fun retry(ws: WebSocket, status: Status, opened: Boolean, farewell: Boolean) {
        if (ws !== socket || !running) return
        socket = null
        if (opened && !farewell) handler.postDelayed(lost, LOST_DELAY_MS)
        PartyState.disconnected()
        if (!opened) {
            hostIndex++
            if (hostIndex % targets.size.coerceAtLeast(1) == 0) {
                round++
                if (reachable()) discover()
            }
        }
        // A goodbye means the game or plugin was closed on purpose, so it is unlikely to be back within the quick retries.
        schedule(status, quick = !farewell)
    }

    // Every address failed, so the PC's LAN address may have changed since pairing.
    private fun discover() {
        if (discovering) return
        discovering = true
        val port = settings.port
        val phoneId = settings.phoneId
        thread(name = TAG) {
            val host = Discovery.find(connectivity, port, phoneId)
            handler.post { found(host) }
        }
    }

    private fun found(host: String?) {
        discovering = false
        if (!running || host == null || host in targets) return
        discovered = host
        hostIndex = 0
        if (socket == null) nudge()
    }

    private fun schedule(status: Status, quick: Boolean = true) {
        PartyState.setStatus(status)
        val searching = status == Status.Connecting
        // The network callback nudges the connection once there is a network that can reach the PC again.
        if (listening && !reachable()) {
            PartyState.setLink(if (searching) Link.Failed else Link.Ok)
            wake.release()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (failingSince == 0L) failingSince = now
        val idle = !quick || now - failingSince >= IDLE_AFTER_MS
        PartyState.setLink(if (!searching) Link.Ok else if (idle) Link.Failed else Link.Searching)
        if (idle) {
            wake.release()
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + IDLE_DELAY_MS, wakeUp)
        } else {
            val delay = min(MAX_DELAY_MS, BASE_DELAY_MS shl min(round, MAX_BACKOFF_STEPS))
            wake.acquire(delay + CONNECT_AWAKE_MS)
            handler.postDelayed(reconnect, delay)
        }
    }

    // The PC is only reachable on a home network or through a VPN like Tailscale, so plain mobile data can't reach it.
    private fun reachable(): Boolean {
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return false
        return !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun versionStatus(theirs: Int) = if (theirs < Protocol.VERSION) Status.UpdatePlugin else Status.UpdateApp

    private inner class Listener(private val host: String, private val phoneId: String, private val secret: ByteArray) : WebSocketListener() {
        private var sealer: Sealer? = null
        private var closeStatus: Status? = null
        private var opened = false
        @Volatile private var farewell = false

        override fun onMessage(webSocket: WebSocket, text: String) {
            val challenge = Protocol.challenge(text)
            if (sealer != null || challenge == null) {
                webSocket.close(POLICY_CLOSE, null)
                return
            }
            if (challenge.version != Protocol.VERSION) {
                closeStatus = versionStatus(challenge.version)
                webSocket.close(NORMAL_CLOSE, null)
                return
            }
            val handshake = Crypto.handshake(secret, challenge.nonce, challenge.ephemeral)
            if (handshake == null) {
                webSocket.close(POLICY_CLOSE, null)
                return
            }
            sealer = Sealer(handshake.keys)
            webSocket.send(Protocol.hello(phoneId, handshake, JobIcons.version))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val sealer = sealer
            val plain = sealer?.open(bytes.toByteArray())
            if (plain == null) {
                closeStatus = Status.NotPaired
                webSocket.close(POLICY_CLOSE, null)
                return
            }
            opened = true
            val text = plain.decodeToString()
            val message = Protocol.parse(text)
            if (message is IconsUpdate) {
                JobIcons.save(context, message.text)
                return
            }
            if (message is ItemIconsUpdate) {
                ItemIcons.save(context, message.text)
                return
            }
            // Read from disk here rather than on the main thread.
            val missingIcons = (message as? CharacterUpdate)?.profile?.let { profile -> ItemIcons.missing(context, profile.gear.map { it.icon }.toSet()) }
            if (message == Bye) farewell = true
            wake.acquire(MESSAGE_AWAKE_MS)
            handler.post {
                if (webSocket !== socket) return@post
                if (PartyState.status.value != Status.Connected) {
                    round = 0
                    failingSince = 0L
                    PartyState.setLink(Link.Ok)
                    handler.removeCallbacks(lost)
                    notifier.found()
                    PartyState.sender = { json ->
                        webSocket === socket && webSocket.send(sealer.seal(json.encodeToByteArray()).toByteString())
                    }
                    PartyState.setStatus(Status.Connected)
                    if (host == discovered) {
                        settings.remember(host)
                        discovered = null
                    }
                    Updater.check(context)
                }
                if (!missingIcons.isNullOrEmpty()) PartyState.sender?.invoke(Protocol.itemIcons(missingIcons))
                if (message != null) {
                    PartyState.apply(message)
                    if (message is Live) notifier.notify(message.item)
                    if (message is History && message.reset) notifier.clearChat(message.all)
                    if (message is Answered) notifier.answered(message)
                    if (message is DutyReply && message.action != DutyAction.LEAVE) notifier.dutied(message)
                    if (message is Dismiss) notifier.dismiss(message.kind)
                    if (message is PluginVersion) Updater.released(context, message.version)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            when (code) {
                NOT_PAIRED -> closeStatus = Status.NotPaired
                UPDATE_NEEDED -> closeStatus = versionStatus(reason.removePrefix("v").toIntOrNull() ?: 0)
            }
            webSocket.close(NORMAL_CLOSE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = done(webSocket)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = done(webSocket)

        private fun done(webSocket: WebSocket) {
            val status = closeStatus ?: Status.Connecting
            val opened = opened
            val farewell = farewell
            wake.acquire(CONNECT_AWAKE_MS)
            handler.post { retry(webSocket, status, opened, farewell) }
        }
    }
}
