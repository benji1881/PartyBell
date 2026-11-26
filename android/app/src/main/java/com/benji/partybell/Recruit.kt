package com.benji.partybell

// A Party Finder listing the phone asks the plugin to post, and the duties it can post for.
data class RecruitDuty(val category: Int, val id: Long, val name: String, val mode: Int = RecruitOptions.NORMAL)

data class RecruitState(val open: Boolean = false, val duties: List<RecruitDuty> = emptyList())

data class RecruitForm(
    val mode: Int = RecruitOptions.NORMAL,
    val category: Int = RecruitOptions.categories(mode).first().first,
    val duty: Long = 0,
    val objective: Int = RecruitOptions.objectives.first().first,
    val completion: Int = RecruitOptions.completions.first().first,
    val loot: Int = RecruitOptions.loots.first().first,
    val slots: List<Int> = RecruitOptions.emptySlots(mode, category),
    val itemLevel: Int = 0,
    val password: Int? = null,
    val beginners: Boolean = false,
    val onePerJob: Boolean = false,
    val noSpectators: Boolean = false,
    val comment: String = "",
) {
    val tooLong: Boolean
        get() = comment.encodeToByteArray().size > RecruitOptions.MAX_COMMENT

    val ready: Boolean
        get() = !tooLong && (duty != 0L || !RecruitOptions.needsDuty(category))

    fun withCategory(category: Int) = copy(category = category, duty = 0, slots = RecruitOptions.emptySlots(mode, category))

    fun withMode(mode: Int) = copy(mode = mode).withCategory(RecruitOptions.categories(mode).first().first)
}

object RecruitOptions {
    const val MAX_COMMENT = 191
    const val MAX_ITEM_LEVEL = 999
    const val MAX_PASSWORD = 9999
    const val ANY = 0
    const val NORMAL = 0
    const val ALLIANCE = 1
    const val CUSTOM_MATCH = 2
    private const val LIGHT_PARTY = 4
    private const val FULL_PARTY = 8
    private const val TEAM = 5
    private const val RAIDS = 5
    private const val PVP = 7
    private val lightParty = setOf(2, 3, 13, 15)
    private val noDuty = setOf(9, 11, 16)

    val modes = listOf(NORMAL to "Normal", ALLIANCE to "Alliance", CUSTOM_MATCH to "Custom Match")

    private val normalCategories = listOf(6, 4, RAIDS, 13, 2, 15, 3, 9, 11, 16)

    val objectives = listOf(1 to "None", 2 to "Duty completion", 4 to "Practice", 8 to "Loot")

    val completions = listOf(1 to "Any", 2 to "Duty complete", 4 to "Duty incomplete", 8 to "Weekly reward unclaimed")

    val loots = listOf(0 to "Normal", 1 to "Greed only", 2 to "Lootmaster")

    fun categories(mode: Int) = when (mode) {
        ALLIANCE -> listOf(RAIDS)
        CUSTOM_MATCH -> listOf(PVP)
        else -> normalCategories
    }.map { it to FinderCategory.label(it) }

    fun groups(mode: Int, category: Int) = when (mode) {
        ALLIANCE -> List(3) { FULL_PARTY }
        CUSTOM_MATCH -> List(2) { TEAM }
        else -> listOf(if (category in lightParty) LIGHT_PARTY else FULL_PARTY)
    }

    fun emptySlots(mode: Int, category: Int) = List(groups(mode, category).sum()) { ANY }

    fun needsDuty(category: Int) = category !in noDuty
}
