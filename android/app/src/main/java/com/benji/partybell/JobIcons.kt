package com.benji.partybell

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.concurrent.thread

object JobIcons {
    private const val FILE = "icons.json"
    private val iconsFlow = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val rolesFlow = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private var loading = false

    val icons: StateFlow<Map<String, ImageBitmap>> = iconsFlow.asStateFlow()

    val roles: StateFlow<Map<String, ImageBitmap>> = rolesFlow.asStateFlow()

    @Volatile
    var version = ""
        private set

    @Synchronized
    fun load(context: Context) {
        if (loading) return
        loading = true
        val file = File(context.filesDir, FILE)
        thread {
            try {
                if (file.exists()) use(file.readText())
            } catch (_: IOException) {
                file.delete()
            }
        }
    }

    fun save(context: Context, text: String) {
        if (!use(text)) return
        try {
            File(context.filesDir, FILE).writeText(text)
        } catch (_: IOException) {
            return
        }
    }

    private fun use(text: String): Boolean = try {
        val json = JSONObject(text)
        iconsFlow.value = masks(json.getJSONObject("icons"), json.getInt("size"))
        rolesFlow.value = json.optJSONObject("roles")?.let { masks(it, json.optInt("roleSize")) }.orEmpty()
        version = json.getString("version")
        true
    } catch (_: JSONException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun masks(icons: JSONObject, size: Int): Map<String, ImageBitmap> = icons.keys().asSequence().mapNotNull { key ->
        val alpha = Base64.getDecoder().decode(icons.getString(key))
        if (size <= 0 || alpha.size != size * size) return@mapNotNull null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        if (bitmap.rowBytes != size) return@mapNotNull null
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(alpha))
        key to bitmap.asImageBitmap()
    }.toMap()
}
