package com.benji.partybell

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class PartyService : Service() {
    companion object {
        private const val ACTION_NUDGE = "com.benji.partybell.NUDGE"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, PartyService::class.java))

        fun nudge(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, PartyService::class.java).setAction(ACTION_NUDGE))

        fun stop(context: Context) =
            context.stopService(Intent(context, PartyService::class.java))
    }

    private lateinit var connection: Connection
    private var started = false

    override fun onCreate() {
        super.onCreate()
        val settings = AppSettings(this)
        val notifier = Notifier(this, settings)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.SERVICE_ID, notifier.serviceNotification(), type)
        JobIcons.load(this)
        TellHistory.load(this)
        connection = Connection(this, settings, notifier)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started && intent?.action == ACTION_NUDGE) {
            connection.nudge()
        } else {
            connection.restart()
            started = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        connection.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
