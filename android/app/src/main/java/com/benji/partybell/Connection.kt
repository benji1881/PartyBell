package com.benji.partybell

import android.os.Handler
import android.os.Looper
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.min

class Connection(private val settings: AppSettings, private val notifier: Notifier) {
    private companion object {
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L
        const val MAX_BACKOFF_STEPS = 5
        const val NORMAL_CLOSE = 1000
        const val POLICY_CLOSE = 1008
        const val NOT_PAIRED = 4001
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var hostIndex = 0
    private var round = 0
    private var running = false

    fun restart() {
        stop()
        running = true
        connect()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        socket?.close(NORMAL_CLOSE, null)
        socket = null
        round = 0
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

        if (PartyState.status.value != Status.NotPaired) PartyState.setStatus(Status.Connecting)
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener(settings.phoneId, secret))
    }

    private fun retry(ws: WebSocket, status: Status, opened: Boolean) {
        if (ws !== socket || !running) return
        socket = null
        PartyState.setStatus(status)
        if (!opened) {
            hostIndex++
            if (hostIndex % settings.hosts.size.coerceAtLeast(1) == 0) round++
        }
        val delay = min(MAX_DELAY_MS, BASE_DELAY_MS shl min(round, MAX_BACKOFF_STEPS))
        handler.postDelayed(::connect, delay)
    }

    private inner class Listener(private val phoneId: String, private val secret: ByteArray) : WebSocketListener() {
        private var opener: Opener? = null
        private var notPaired = false
        private var opened = false

        override fun onMessage(webSocket: WebSocket, text: String) {
            val serverNonce = Protocol.challenge(text)
            if (opener != null || serverNonce == null) {
                webSocket.close(POLICY_CLOSE, null)
                return
            }
            val phoneNonce = Crypto.nonce()
            opener = Opener(Crypto.sessionKey(secret, serverNonce, phoneNonce))
            webSocket.send(Protocol.hello(phoneId, phoneNonce, Crypto.proof(secret, serverNonce, phoneNonce)))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val plain = opener?.open(bytes.toByteArray())
            if (plain == null) {
                notPaired = true
                webSocket.close(POLICY_CLOSE, null)
                return
            }
            opened = true
            val message = Protocol.parse(plain.decodeToString())
            handler.post {
                if (webSocket !== socket) return@post
                if (PartyState.status.value != Status.Connected) {
                    round = 0
                    PartyState.setStatus(Status.Connected)
                }
                if (message != null) {
                    PartyState.apply(message)
                    if (message is Live) notifier.notify(message.item)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (code == NOT_PAIRED) notPaired = true
            webSocket.close(NORMAL_CLOSE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = done(webSocket)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = done(webSocket)

        private fun done(webSocket: WebSocket) {
            val status = if (notPaired) Status.NotPaired else Status.Connecting
            val opened = opened
            handler.post { retry(webSocket, status, opened) }
        }
    }
}
