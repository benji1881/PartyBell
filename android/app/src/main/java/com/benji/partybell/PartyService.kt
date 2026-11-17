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
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, PartyService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, PartyService::class.java))
    }

    private lateinit var connection: Connection

    override fun onCreate() {
        super.onCreate()
        val settings = AppSettings(this)
        val notifier = Notifier(this, settings)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.SERVICE_ID, notifier.serviceNotification(), type)
        JobIcons.load(this)
        connection = Connection(this, settings, notifier)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connection.restart()
        return START_STICKY
    }

    override fun onDestroy() {
        connection.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
