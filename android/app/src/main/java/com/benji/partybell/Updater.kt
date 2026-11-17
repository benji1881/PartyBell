package com.benji.partybell

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.os.SystemClock
import android.provider.Settings
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
import kotlin.concurrent.thread

data class AppUpdate(val version: String, val url: String, val progress: Int? = null, val failed: Boolean = false)

object Updater {
    private const val LATEST = "https://api.github.com/repos/benji1881/PartyBell/releases/latest"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val APK_NAME = "PartyBell.apk"
    private val versionPattern = Regex("\\d+(\\.\\d+)*")
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val updateFlow = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = updateFlow.asStateFlow()

    @Volatile private var checking = false
    @Volatile private var checkedAt = 0L

    fun current(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun check(context: Context) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        if (debuggable(app) || checking || (checkedAt != 0L && now - checkedAt < CHECK_INTERVAL_MS)) return
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

    fun install(context: Context) {
        val update = updateFlow.value ?: return
        if (update.progress != null) return
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri()))
            return
        }
        Notifier(app, AppSettings(app)).updated()
        updateFlow.value = update.copy(progress = 0, failed = false)
        thread {
            try {
                download(app, update.url)
            } catch (_: IOException) {
                failed()
            } catch (_: SecurityException) {
                failed()
            }
        }
    }

    fun cancelled() {
        updateFlow.value = updateFlow.value?.copy(progress = null)
    }

    fun failed() {
        updateFlow.value = updateFlow.value?.copy(progress = null, failed = true)
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

    private fun download(app: Context, url: String) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body?.takeIf { response.isSuccessful } ?: throw IOException("download failed")
                    val total = body.contentLength()
                    session.openWrite(APK_NAME, 0, total).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                done += read
                                if (total > 0) progress((done * 100 / total).toInt())
                            }
                            session.fsync(out)
                        }
                    }
                }
                progress(100)
                val intent = Intent(app, InstallReceiver::class.java)
                session.commit(PendingIntent.getBroadcast(app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)
            } catch (e: IOException) {
                session.abandon()
                throw e
            }
        }
    }

    private fun progress(percent: Int) {
        val update = updateFlow.value ?: return
        if (update.progress != percent) updateFlow.value = update.copy(progress = percent)
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
