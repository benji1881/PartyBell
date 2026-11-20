package com.benji.partybell

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

sealed interface Incoming

data class PartyUpdate(val party: Party) : Incoming

data class History(val items: List<FeedItem>, val reset: Boolean, val all: Boolean) : Incoming

data class Live(val item: FeedItem) : Incoming

data class Said(val id: Long, val ok: Boolean, val reason: String) : Incoming

data class IconsUpdate(val text: String) : Incoming

data class Answered(val ready: Boolean, val ok: Boolean, val reason: String) : Incoming

data class Dismiss(val kind: String) : Incoming

data class FriendsUpdate(val friends: Friends) : Incoming

data class CompanyUpdate(val member: Boolean) : Incoming

data class Invited(val id: Long, val ok: Boolean, val reason: String) : Incoming

data class ListingsUpdate(val listings: Listings) : Incoming

data class Finding(val ok: Boolean, val reason: String) : Incoming

data class Opened(val ok: Boolean, val reason: String) : Incoming

data class Joining(val ok: Boolean, val reason: String) : Incoming

data class Refreshing(val ok: Boolean, val reason: String) : Incoming

data class PluginVersion(val version: String) : Incoming

data class QueueUpdate(val status: QueueStatus?) : Incoming

data class CharacterUpdate(val profile: Profile?) : Incoming

data class DutyReply(val action: String, val ok: Boolean, val reason: String) : Incoming

data object Bye : Incoming

data class SavedTells(val tells: List<ChatItem>, val viewed: Map<String, Long>)

data class Challenge(val version: Int, val nonce: ByteArray, val ephemeral: ByteArray)

object Protocol {
    const val VERSION = 6

    fun parse(text: String): Incoming? = try {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "party" -> PartyUpdate(party(json))
            "history" -> History(objects(json, "items").mapNotNull(::item), json.optBoolean("reset"), json.optBoolean("all"))
            "said" -> Said(json.optLong("id"), json.optBoolean("ok"), json.optString("reason"))
            "icons" -> IconsUpdate(text)
            "answered" -> Answered(json.optBoolean("ready"), json.optBoolean("ok"), json.optString("reason"))
            "dismiss" -> Dismiss(json.optString("kind"))
            "friends" -> FriendsUpdate(friends(json))
            "company" -> CompanyUpdate(json.optBoolean("member"))
            "invited" -> Invited(json.optLong("id"), json.optBoolean("ok"), json.optString("reason"))
            "listings" -> ListingsUpdate(listings(json))
            "finding" -> Finding(json.optBoolean("ok"), json.optString("reason"))
            "opened" -> Opened(json.optBoolean("ok"), json.optString("reason"))
            "joining" -> Joining(json.optBoolean("ok"), json.optString("reason"))
            "refreshing" -> Refreshing(json.optBoolean("ok"), json.optString("reason"))
            "plugin" -> PluginVersion(json.optString("version"))
            "queuestatus" -> QueueUpdate(json.optJSONObject("status")?.let(::queue))
            "character" -> CharacterUpdate(json.optJSONObject("character")?.let(::profile))
            "duty" -> DutyReply(json.optString("action"), json.optBoolean("ok"), json.optString("reason"))
            "bye" -> Bye
            else -> item(json)?.let(::Live)
        }
    } catch (_: JSONException) {
        null
    }

    fun challenge(text: String): Challenge? = try {
        val json = JSONObject(text)
        val nonce = Base64.getDecoder().decode(json.getString("nonce"))
        Challenge(json.optInt("v", 1), nonce, Base64.getDecoder().decode(json.optString("ephemeral"))).takeIf { nonce.size == Crypto.NONCE_SIZE }
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun savedTells(saved: SavedTells): String = JSONObject()
        .put("tells", JSONArray(saved.tells.map(::chat)))
        .put("viewed", JSONObject(saved.viewed))
        .toString()

    fun savedTells(text: String): SavedTells? = try {
        val json = JSONObject(text)
        val viewed = json.optJSONObject("viewed") ?: JSONObject()
        SavedTells(
            objects(json, "tells").mapNotNull { item(it) as? ChatItem },
            viewed.keys().asSequence().associateWith { viewed.optLong(it) },
        )
    } catch (_: JSONException) {
        null
    }

    fun hello(id: String, handshake: Handshake, icons: String): String = JSONObject()
        .put("v", VERSION)
        .put("id", id)
        .put("nonce", Base64.getEncoder().encodeToString(handshake.nonce))
        .put("ephemeral", Base64.getEncoder().encodeToString(handshake.ephemeral))
        .put("proof", Base64.getEncoder().encodeToString(handshake.proof))
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

    fun finder(category: Int): String = JSONObject()
        .put("type", "finder")
        .put("category", category)
        .toString()

    fun refreshFriends(): String = JSONObject()
        .put("type", "friendsrefresh")
        .toString()

    fun open(id: String): String = JSONObject()
        .put("type", "open")
        .put("id", id)
        .toString()

    fun join(id: String): String = JSONObject()
        .put("type", "join")
        .put("id", id)
        .toString()

    fun answer(ready: Boolean): String = JSONObject()
        .put("type", "answer")
        .put("ready", ready)
        .toString()

    fun duty(action: String): String = JSONObject()
        .put("type", "duty")
        .put("action", action)
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

    private fun listings(json: JSONObject) = Listings(
        category = json.optInt("category"),
        time = json.optLong("time"),
        list = objects(json, "listings").map {
            Listing(
                id = it.optString("id"),
                duty = it.optString("duty"),
                code = it.optString("code"),
                category = it.optInt("category"),
                description = GameText.clean(it.optString("description")),
                leader = it.optString("leader"),
                world = it.optString("world"),
                seconds = it.optInt("seconds"),
                ilvl = it.optInt("ilvl"),
                slots = objects(it, "slots").map { slot -> Slot(slot.optString("job"), slot.optInt("role")) },
                jobs = strings(it, "jobs"),
                fits = it.optBoolean("fits"),
                own = it.optBoolean("own"),
                locked = it.optBoolean("locked"),
                practice = it.optBoolean("practice"),
                loot = it.optBoolean("loot"),
                completion = it.optBoolean("completion"),
                cleared = it.optBoolean("cleared"),
                beginners = it.optBoolean("beginners"),
                blocked = it.optBoolean("blocked"),
            )
        },
    )

    private fun queue(json: JSONObject) = QueueStatus(
        state = QueueState.of(json.optString("state")),
        job = json.optString("job"),
        duties = strings(json, "duties"),
        entered = json.optLong("entered"),
        ready = json.optLong("ready"),
        expires = json.optLong("expires"),
        position = json.optInt("position", -1),
        wait = json.optInt("wait", -1),
        counts = objects(json, "counts").map { QueueCount(it.optString("role"), it.optInt("found"), it.optInt("needed")) },
        actions = json.optBoolean("actions"),
    )

    private fun profile(json: JSONObject) = Profile(
        name = json.optString("name"),
        world = json.optString("world"),
        dataCenter = json.optString("dataCenter"),
        job = json.optString("job"),
        jobName = json.optString("jobName"),
        role = json.optInt("role"),
        level = json.optInt("level"),
        max = json.optBoolean("max"),
        exp = json.optLong("exp"),
        expNeeded = json.optLong("expNeeded"),
        itemLevel = json.optInt("itemLevel"),
        zone = json.optString("zone"),
        area = json.optString("area"),
        instance = json.optInt("instance"),
        gear = objects(json, "gear").map {
            GearPiece(it.optInt("slot"), it.optString("name"), it.optInt("level"), it.optInt("condition"), it.optInt("spiritbond"))
        },
        currencies = objects(json, "currencies").map { Currency(it.optLong("id"), it.optString("name"), it.optLong("count")) },
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
            GameText.clean(json.optString("text")),
            json.optString("channel"),
            json.optBoolean("self"),
            json.optLong("time"),
            json.optString("peer"),
        )
        else -> null
    }

    private fun chat(item: ChatItem): JSONObject = JSONObject()
        .put("type", "chat")
        .put("name", item.name)
        .put("job", item.job)
        .put("role", item.role)
        .put("text", item.text)
        .put("channel", item.channel)
        .put("self", item.self)
        .put("time", item.time)
        .put("peer", item.peer)

    private fun strings(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.optString(it) }
    }

    private fun objects(json: JSONObject, key: String): List<JSONObject> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }
}
