package com.benji.partybell

// Turns the plugin's reason codes (plugin/Reason.cs) into text for the phone.
object Reasons {
    const val NOT_CONNECTED = "Not connected"
    const val TOO_LONG = "Too long for the game chat"
    const val COMMAND = "Commands can't be sent from the phone"

    // Used by the phone itself when a request couldn't be sent.
    const val OFFLINE = "offline"

    private const val DISABLED = "disabled"
    private const val NOT_READY = "not ready"
    private const val IN_DUTY = "in duty"
    private const val WINDOW_OPEN = "window open"
    private const val GAME_NOT_READY = "The game isn't ready"
    private const val GAME_REFUSED = "The game refused it"
    private const val REQUEST_REFUSED = "The game refused the request"

    fun chat(code: String) = when (code) {
        DISABLED -> "Writing from phones is off in the plugin"
        "too long" -> TOO_LONG
        "command" -> COMMAND
        "no party" -> "You're not in a party"
        "no alliance" -> "You're not in an alliance"
        "no fc" -> "You're not in a Free Company"
        NOT_READY -> GAME_NOT_READY
        "invalid" -> "Not a valid name"
        "unsupported" -> "The game can't show some of these characters"
        "not delivered" -> "The game couldn't deliver the tell"
        else -> GAME_REFUSED
    }

    fun invite(code: String) = when (code) {
        DISABLED -> "Party invites from phones are off in the plugin"
        "party full" -> "Your party is full"
        "not leader" -> "Only the party leader can invite"
        "not found" -> "Not on your friend list"
        OFFLINE -> "They're offline"
        NOT_READY -> GAME_NOT_READY
        else -> "The game refused the invite"
    }

    fun find(code: String) = when (code) {
        NOT_READY -> GAME_NOT_READY
        IN_DUTY -> "The Party Finder can't be read in a duty"
        WINDOW_OPEN -> "The Party Finder is open in the game"
        else -> REQUEST_REFUSED
    }

    fun friends(code: String) = when (code) {
        NOT_READY -> GAME_NOT_READY
        WINDOW_OPEN -> "Your friend list is open in the game"
        else -> REQUEST_REFUSED
    }

    fun open(code: String) = when (code) {
        NOT_READY -> GAME_NOT_READY
        IN_DUTY -> "The Party Finder can't be opened in a duty"
        else -> "The game couldn't open the listing"
    }

    fun join(code: String) = when (code) {
        DISABLED -> "Joining listings from phones is off in the plugin"
        NOT_READY -> GAME_NOT_READY
        IN_DUTY -> "You can't join a party in a duty"
        "busy" -> "Already joining a party"
        "timeout" -> "The game didn't let you join"
        else -> "The game couldn't join the party"
    }

    fun recruit(code: String) = when (code) {
        DISABLED -> "Posting listings from phones is off in the plugin"
        NOT_READY -> GAME_NOT_READY
        IN_DUTY -> "You can't recruit in a duty"
        "queued" -> "You're in a duty queue"
        "in party" -> "You're already in a party"
        "recruiting" -> "You're already recruiting"
        WINDOW_OPEN -> "The recruitment window is open in the game"
        "not found" -> "That duty isn't in the game's list"
        "too long" -> "The comment is too long"
        "busy" -> "Already posting a listing"
        "timeout" -> "The game didn't post the listing"
        OFFLINE -> NOT_CONNECTED
        else -> "The game couldn't post the listing"
    }

    fun duty(code: String) = when (code) {
        DISABLED -> "Accepting duties from phones is off in the plugin"
        "not queued" -> "You're not in the queue"
        NOT_READY -> "The duty isn't waiting for you"
        "no window" -> "The duty window isn't open in the game"
        OFFLINE -> NOT_CONNECTED
        else -> GAME_REFUSED
    }

    fun bis(code: String) = when (code) {
        NOT_READY -> GAME_NOT_READY
        "invalid" -> "Not an etro.gg gear set link"
        "not found" -> "Couldn't read that set from etro.gg"
        "wrong job" -> "That set is for another job"
        OFFLINE -> NOT_CONNECTED
        else -> REQUEST_REFUSED
    }

    fun readyCheck(code: String) = when (code) {
        DISABLED -> "Answering from phones is off in the plugin"
        "no ready check" -> "The ready check is over"
        "answered" -> "Already answered in game"
        "no prompt" -> "The ready check window isn't open"
        OFFLINE -> NOT_CONNECTED
        else -> GAME_REFUSED
    }
}
