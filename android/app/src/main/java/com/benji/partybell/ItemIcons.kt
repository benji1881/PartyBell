package com.benji.partybell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

// Gear icons are kept on disk by icon id, so each one crosses the network once instead of on every connect and job change.
object ItemIcons {
    private const val DIR = "items"
    private val iconsFlow = MutableStateFlow<Map<Long, ImageBitmap>>(emptyMap())
    // Asked for on this connection, so character updates every few seconds don't ask again while the reply is on its way.
    private val asked = mutableSetOf<Long>()

    // Only the icons of the gear being worn are held in memory.
    val icons: StateFlow<Map<Long, ImageBitmap>> = iconsFlow.asStateFlow()

    // Shows the saved icons for this gear and returns the ids the PC still has to send.
    @Synchronized
    fun missing(context: Context, ids: Set<Long>): List<Long> {
        val shown = iconsFlow.value
        val saved = (ids - shown.keys).mapNotNull { id ->
            BitmapFactory.decodeFile(file(context, id).path)?.let { id to it.asImageBitmap() }
        }
        iconsFlow.value = shown.filterKeys { it in ids } + saved
        return (ids - iconsFlow.value.keys - asked).also { asked += it }.toList()
    }

    @Synchronized
    fun save(context: Context, text: String) {
        val received = try {
            JobIcons.pictures(JSONObject(text)).mapNotNull { (key, icon) -> key.toLongOrNull()?.let { it to icon } }.toMap()
        } catch (_: JSONException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        iconsFlow.value += received
        File(context.cacheDir, DIR).mkdirs()
        received.forEach { (id, icon) ->
            try {
                file(context, id).outputStream().use { icon.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: IOException) {
                return@forEach
            }
        }
    }

    // A request lost with its connection is asked again on the next one.
    @Synchronized
    fun forget() = asked.clear()

    private fun file(context: Context, id: Long) = File(File(context.cacheDir, DIR), "$id.png")
}
