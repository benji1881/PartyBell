package com.benji.partybell

import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

sealed interface Incoming

data class PartyUpdate(val party: Party) : Incoming

data class History(val items: List<FeedItem>) : Incoming

data class Live(val item: FeedItem) : Incoming

object Protocol {
    fun parse(text: String): Incoming? = try {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "party" -> PartyUpdate(party(json))
            "history" -> History(objects(json, "items").mapNotNull(::item))
            else -> item(json)?.let(::Live)
        }
    } catch (_: JSONException) {
        null
    }

    fun challenge(text: String): ByteArray? = try {
        Base64.getDecoder().decode(JSONObject(text).getString("nonce")).takeIf { it.size == Crypto.NONCE_SIZE }
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun hello(id: String, nonce: ByteArray, proof: ByteArray): String = JSONObject()
        .put("id", id)
        .put("nonce", Base64.getEncoder().encodeToString(nonce))
        .put("proof", Base64.getEncoder().encodeToString(proof))
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
        capacity = json.optInt("capacity", 8),
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
            json.optLong("time"),
        )
        else -> null
    }

    private fun objects(json: JSONObject, key: String): List<JSONObject> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }
}
