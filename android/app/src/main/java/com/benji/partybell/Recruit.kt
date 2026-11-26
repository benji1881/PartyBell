package com.benji.partybell

// A Party Finder listing the phone asks the plugin to post, and the duties it can post for.
data class RecruitDuty(val category: Int, val id: Long, val name: String)

data class RecruitState(val open: Boolean = false, val duties: List<RecruitDuty> = emptyList())

data class RecruitForm(
    val category: Int = RecruitOptions.categories.first().first,
    val duty: Long = 0,
    val objective: Int = RecruitOptions.objectives.first().first,
    val completion: Int = RecruitOptions.completions.first().first,
    val loot: Int = RecruitOptions.loots.first().first,
    val slots: List<Int> = List(RecruitOptions.size(category)) { RecruitOptions.ANY },
    val itemLevel: Int = 0,
    val password: Int? = null,
    val beginners: Boolean = false,
    val onePerJob: Boolean = false,
    val comment: String = "",
) {
    val tooLong: Boolean
        get() = comment.encodeToByteArray().size > RecruitOptions.MAX_COMMENT

    val ready: Boolean
        get() = !tooLong && (duty != 0L || !RecruitOptions.needsDuty(category))

    fun withCategory(category: Int) = copy(category = category, duty = 0, slots = List(RecruitOptions.size(category)) { RecruitOptions.ANY })
}

object RecruitOptions {
    const val MAX_COMMENT = 191
    const val MAX_ITEM_LEVEL = 999
    const val MAX_PASSWORD = 9999
    const val ANY = 0
    private const val LIGHT_PARTY = 4
    private const val FULL_PARTY = 8
    private val lightParty = setOf(2, 3, 13, 15)
    private val noDuty = setOf(9, 11, 16)

    val categories = listOf(6, 4, 5, 13, 2, 15, 3, 9, 11, 16).map { it to FinderCategory.label(it) }

    val objectives = listOf(1 to "None", 2 to "Duty completion", 4 to "Practice", 8 to "Loot")

    val completions = listOf(1 to "Any", 2 to "Duty complete", 4 to "Duty incomplete", 8 to "Weekly reward unclaimed")

    val loots = listOf(0 to "Normal", 1 to "Greed only", 2 to "Lootmaster")

    fun size(category: Int) = if (category in lightParty) LIGHT_PARTY else FULL_PARTY

    fun needsDuty(category: Int) = category !in noDuty
}
