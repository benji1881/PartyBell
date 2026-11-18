package com.benji.partybell

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppUpdate(val version: String, val url: String)

object Updater {
    private const val LATEST = "https://api.github.com/repos/benji1881/PartyBell/releases/latest"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private val versionPattern = Regex("\\d+(\\.\\d+)*")
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val updateFlow = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = updateFlow.asStateFlow()

    @Volatile private var checking = false
    @Volatile private var checkedAt = 0L
    @Volatile private var releasedSeen = ""

    fun current(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun released(context: Context, version: String) {
        if (version == releasedSeen || !versionPattern.matches(version) || !newer(version, current(context))) return
        releasedSeen = version
        check(context, force = true)
    }

    fun check(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        if (debuggable(app) || checking || (!force && checkedAt != 0L && now - checkedAt < CHECK_INTERVAL_MS)) return
        checking = true
        val request = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                checking = false
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        checkedAt = now
                        found(app, JSONObject(it.body?.string().orEmpty()))
                    }
                }
                checking = false
            }
        })
    }

    fun download(context: Context) {
        val update = updateFlow.value ?: return
        Notifier(context, AppSettings(context)).updated()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, update.url.toUri()))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun found(app: Context, release: JSONObject) {
        val version = release.optString("tag_name").removePrefix("v")
        val assets = release.optJSONArray("assets")
        val url = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?.takeIf { it.startsWith("https://") }
        if (url == null || !versionPattern.matches(version) || !newer(version, current(app))) {
            updateFlow.value = null
            Notifier(app, AppSettings(app)).updated()
            return
        }
        if (updateFlow.value?.version != version) updateFlow.value = AppUpdate(version, url)
        val settings = AppSettings(app)
        if (settings.notifiedUpdate != version) {
            settings.notifiedUpdate = version
            Notifier(app, settings).update(version)
        }
    }

    private fun newer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun debuggable(app: Context) = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
