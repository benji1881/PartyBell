package com.benji.partybell

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
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
import kotlin.math.min

class Connection(private val context: Context, private val settings: AppSettings, private val notifier: Notifier) {
    private companion object {
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L
        const val MAX_BACKOFF_STEPS = 5
        const val NORMAL_CLOSE = 1000
        const val POLICY_CLOSE = 1008
        const val NOT_PAIRED = 4001
        const val UPDATE_NEEDED = 4002
        const val LOST_DELAY_MS = 60_000L
        const val IDLE_AFTER_MS = 10 * 60_000L
        const val IDLE_DELAY_MS = 5 * 60_000L
        val waiting = setOf(Status.NotPaired, Status.UpdatePlugin, Status.UpdateApp)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val lost = Runnable { notifier.lost() }
    private val reconnect = Runnable { connect() }
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { nudge() }
        }
    }
    private var socket: WebSocket? = null
    private var hostIndex = 0
    private var round = 0
    private var failingSince = 0L
    private var running = false
    private var listening = false

    fun restart() {
        stop()
        running = true
        listening = runCatching { connectivity.registerDefaultNetworkCallback(network) }.isSuccess
        connect()
    }

    fun nudge() {
        if (!running || socket != null) return
        handler.removeCallbacks(reconnect)
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
        socket?.close(NORMAL_CLOSE, null)
        socket = null
        round = 0
        failingSince = 0L
        PartyState.setLink(Link.Ok)
        notifier.found()
        PartyState.disconnected()
        PartyState.setStatus(Status.Off)
    }

    private fun connect() {
        val hosts = settings.hosts
        val secret = Pairing.secret(settings.key)
        if (!running || hosts.isEmpty() || secret == null) return

        val url = try {
            HttpUrl.Builder()
                .scheme("http")
                .host(hosts[hostIndex % hosts.size])
                .port(settings.port)
                .build()
        } catch (_: IllegalArgumentException) {
            PartyState.setStatus(Status.NotPaired)
            return
        }

        if (PartyState.status.value !in waiting) PartyState.setStatus(Status.Connecting)
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener(settings.phoneId, secret))
    }

    private fun retry(ws: WebSocket, status: Status, opened: Boolean, farewell: Boolean) {
        if (ws !== socket || !running) return
        socket = null
        if (opened && !farewell) handler.postDelayed(lost, LOST_DELAY_MS)
        PartyState.disconnected()
        PartyState.setStatus(status)
        if (!opened) {
            hostIndex++
            if (hostIndex % settings.hosts.size.coerceAtLeast(1) == 0) round++
        }
        val searching = status == Status.Connecting
        if (listening && connectivity.activeNetwork == null) {
            PartyState.setLink(if (searching) Link.Failed else Link.Ok)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (failingSince == 0L) failingSince = now
        val idle = now - failingSince >= IDLE_AFTER_MS
        PartyState.setLink(if (!searching) Link.Ok else if (idle) Link.Failed else Link.Searching)
        val delay = if (idle) IDLE_DELAY_MS else min(MAX_DELAY_MS, BASE_DELAY_MS shl min(round, MAX_BACKOFF_STEPS))
        handler.postDelayed(reconnect, delay)
    }

    private fun versionStatus(theirs: Int) = if (theirs < Protocol.VERSION) Status.UpdatePlugin else Status.UpdateApp

    private inner class Listener(private val phoneId: String, private val secret: ByteArray) : WebSocketListener() {
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
            val phoneNonce = Crypto.nonce()
            sealer = Sealer(Crypto.sessionKeys(secret, challenge.nonce, phoneNonce))
            webSocket.send(Protocol.hello(phoneId, phoneNonce, Crypto.proof(secret, challenge.nonce, phoneNonce), JobIcons.version))
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
            if (message == Bye) farewell = true
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
                    Updater.check(context)
                }
                if (message != null) {
                    PartyState.apply(message)
                    if (message is Live) notifier.notify(message.item)
                    if (message is History && message.reset) notifier.clearChat(message.all)
                    if (message is Answered) notifier.answered(message)
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
            handler.post { retry(webSocket, status, opened, farewell) }
        }
    }
}
