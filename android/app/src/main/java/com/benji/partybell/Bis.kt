package com.benji.partybell

// The linked best in slot set for the current job, compared with what is equipped.
data class BisSlot(val slot: Int, val name: String, val level: Int, val icon: Long, val done: Boolean)

data class BisItem(val name: String, val icon: Long)

data class BisMateria(val name: String, val icon: Long, val stat: String, val value: Int, val count: Int)

data class BisStat(val name: String, val value: Int)

data class BisSet(
    val name: String,
    val slots: List<BisSlot>,
    val food: BisItem?,
    val materia: List<BisMateria>,
    val gcd: Double,
    val stats: List<BisStat>,
) {
    val done: Int
        get() = slots.count { it.done }

    val icons: Set<Long>
        get() = slots.map { it.icon }.toSet() + materia.map { it.icon } + listOfNotNull(food?.icon)

    fun slot(slot: Int) = slots.firstOrNull { it.slot == slot }
}

data class Bis(val link: String? = null, val set: BisSet? = null) {
    val site: String
        get() = if (link?.contains(XIVGEAR) == true) XIVGEAR else ETRO
}

private const val XIVGEAR = "xivgear.app"
private const val ETRO = "etro.gg"
