package com.benji.partybell.ui

// The game's job roles, as the plugin sends them.
internal const val ROLE_TANK = 1
internal const val ROLE_MELEE = 2
internal const val ROLE_RANGED = 3
internal const val ROLE_HEALER = 4

fun roleName(role: Int): String = when (role) {
    ROLE_TANK -> "tank"
    ROLE_HEALER -> "healer"
    ROLE_MELEE, ROLE_RANGED -> "dps"
    else -> "any"
}

internal val RoleOrder = listOf(ROLE_TANK, ROLE_HEALER, ROLE_MELEE, ROLE_RANGED)

fun roleRank(role: Int): Int = RoleOrder.indexOf(role).let { if (it < 0) RoleOrder.size else it }

fun roleTitle(role: String) = when (role) {
    "tank" -> "Tanks"
    "healer" -> "Healers"
    "dps" -> "DPS"
    "players" -> "Players"
    else -> "Other"
}
