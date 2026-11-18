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
        val size = json.getInt("size")
        val icons = json.getJSONObject("icons")
        val bitmaps = icons.keys().asSequence().mapNotNull { job ->
            val alpha = Base64.getDecoder().decode(icons.getString(job))
            if (size <= 0 || alpha.size != size * size) return@mapNotNull null
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
            if (bitmap.rowBytes != size) return@mapNotNull null
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(alpha))
            job to bitmap.asImageBitmap()
        }.toMap()
        iconsFlow.value = bitmaps
        rolesFlow.value = roles(json)
        version = json.getString("version")
        true
    } catch (_: JSONException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun roles(json: JSONObject): Map<String, ImageBitmap> {
        val size = json.optInt("roleSize")
        val roles = json.optJSONObject("roles") ?: return emptyMap()
        if (size <= 0) return emptyMap()
        return roles.keys().asSequence().mapNotNull { role ->
            val rgba = Base64.getDecoder().decode(roles.getString(role))
            if (rgba.size != size * size * 4) return@mapNotNull null
            val pixels = IntArray(size * size) {
                val i = it * 4
                (rgba[i + 3].toInt() and 0xFF shl 24) or (rgba[i].toInt() and 0xFF shl 16) or
                    (rgba[i + 1].toInt() and 0xFF shl 8) or (rgba[i + 2].toInt() and 0xFF)
            }
            role to Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.toMap()
    }
}
