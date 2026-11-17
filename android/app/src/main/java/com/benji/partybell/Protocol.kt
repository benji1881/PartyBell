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

data class Answered(val ready: Boolean, val ok: Boolean, val reason: String) : Incoming

data class Dismiss(val kind: String) : Incoming

data class FriendsUpdate(val friends: Friends) : Incoming

data class Invited(val id: Long, val ok: Boolean, val reason: String) : Incoming

data class Challenge(val version: Int, val nonce: ByteArray)

object Protocol {
    const val VERSION = 3

    fun parse(text: String): Incoming? = try {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "party" -> PartyUpdate(party(json))
            "history" -> History(objects(json, "items").mapNotNull(::item), json.optBoolean("reset"))
            "said" -> Said(json.optLong("id"), json.optBoolean("ok"), json.optString("reason"))
            "icons" -> IconsUpdate(text)
            "answered" -> Answered(json.optBoolean("ready"), json.optBoolean("ok"), json.optString("reason"))
            "dismiss" -> Dismiss(json.optString("kind"))
            "friends" -> FriendsUpdate(friends(json))
            "invited" -> Invited(json.optLong("id"), json.optBoolean("ok"), json.optString("reason"))
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

    fun say(id: Long, channel: String, text: String, to: String): String = JSONObject()
        .put("type", "say")
        .put("id", id)
        .put("channel", channel)
        .put("text", text)
        .put("to", to)
        .toString()

    fun invite(id: Long, friend: Friend): String = JSONObject()
        .put("type", "invite")
        .put("id", id)
        .put("name", friend.name)
        .put("world", friend.world)
        .toString()

    fun answer(ready: Boolean): String = JSONObject()
        .put("type", "answer")
        .put("ready", ready)
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

    private fun friends(json: JSONObject) = Friends(
        loaded = json.optBoolean("loaded"),
        list = objects(json, "friends").map {
            Friend(
                name = it.optString("name"),
                world = it.optString("world"),
                job = it.optString("job"),
                role = it.optInt("role"),
                status = FriendStatus.of(it.optString("status")),
            )
        },
    )

    private fun item(json: JSONObject): FeedItem? = when (json.optString("type")) {
        "event" -> EventKind.of(json.optString("kind"))?.let {
            PartyEventItem(
                it,
                json.optString("name"),
                json.optString("job"),
                json.optInt("role"),
                json.optLong("time"),
                json.optInt("count"),
                json.optString("detail"),
                json.optBoolean("answerable"),
            )
        }
        "chat" -> ChatItem(
            json.optString("name"),
            json.optString("job"),
            json.optInt("role"),
            json.optString("text"),
            json.optString("channel"),
            json.optBoolean("self"),
            json.optLong("time"),
            json.optString("peer"),
        )
        else -> null
    }

    private fun objects(json: JSONObject, key: String): List<JSONObject> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }
}
