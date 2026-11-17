package com.benji.partybell

import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

sealed interface Incoming

data class PartyUpdate(val party: Party) : Incoming

data class History(val items: List<FeedItem>, val reset: Boolean) : Incoming

data class Live(val item: FeedItem) : Incoming

data class Said(val id: Long, val ok: Boolean, val reason: String) : Incoming

data class IconsUpdate(val text: String) : Incoming

data class Challenge(val version: Int, val nonce: ByteArray)

object Protocol {
    const val VERSION = 2

    fun parse(text: String): Incoming? = try {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "party" -> PartyUpdate(party(json))
            "history" -> History(objects(json, "items").mapNotNull(::item), json.optBoolean("reset"))
            "said" -> Said(json.optLong("id"), json.optBoolean("ok"), json.optString("reason"))
            "icons" -> IconsUpdate(text)
            else -> item(json)?.let(::Live)
        }
    } catch (_: JSONException) {
        null
    }

    fun challenge(text: String): Challenge? = try {
        val json = JSONObject(text)
        val nonce = Base64.getDecoder().decode(json.getString("nonce"))
        Challenge(json.optInt("v", 1), nonce).takeIf { nonce.size == Crypto.NONCE_SIZE }
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun hello(id: String, nonce: ByteArray, proof: ByteArray, icons: String): String = JSONObject()
        .put("v", VERSION)
        .put("id", id)
        .put("nonce", Base64.getEncoder().encodeToString(nonce))
        .put("proof", Base64.getEncoder().encodeToString(proof))
        .put("icons", icons)
        .toString()

    fun say(id: Long, channel: String, text: String): String = JSONObject()
        .put("type", "say")
        .put("id", id)
        .put("channel", channel)
        .put("text", text)
        .toString()

    private fun party(json: JSONObject) = Party(
        members = objects(json, "members").map {
            Member(
                name = it.optString("name"),
                world = it.optString("world"),
                job = it.optString("job"),
                role = it.optInt("role"),
                level = it.optInt("level"),
                leader = it.optBoolean("leader"),
                group = it.optInt("group"),
            )
        },
        capacity = json.optInt("capacity", Party.GROUP_SIZE),
    )

    private fun item(json: JSONObject): FeedItem? = when (json.optString("type")) {
        "event" -> EventKind.of(json.optString("kind"))?.let {
            PartyEventItem(it, json.optString("name"), json.optString("job"), json.optInt("role"), json.optLong("time"))
        }
        "chat" -> ChatItem(
            json.optString("name"),
            json.optString("job"),
            json.optInt("role"),
            json.optString("text"),
            json.optString("channel"),
            json.optBoolean("self"),
            json.optLong("time"),
        )
        else -> null
    }

    private fun objects(json: JSONObject, key: String): List<JSONObject> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }
}
