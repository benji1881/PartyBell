package com.benji.partybell.ui

import com.benji.partybell.Job
import com.benji.partybell.Listing

// Decides which Party Finder listings the filters keep and which words to highlight.

internal enum class Goal(val label: String, val keeps: (Listing) -> Boolean) {
    Any("Any", { true }),
    Practice("Practice", { it.practice }),
    Completion("Completion", { it.completion }),
    Loot("Loot", { it.loot }),
}

internal fun keepsJobs(listing: Listing, fits: Boolean, picks: Set<String>) =
    (!fits && picks.isEmpty()) || (fits && listing.fits) || listing.jobs.any { it in picks }

internal fun jobSummary(fits: Boolean, picks: Set<String>, jobs: List<Job>, shown: Int = Int.MAX_VALUE): String {
    val order = jobs.map { it.name }
    val names = listOfNotNull("My job".takeIf { fits }) + picks.sortedBy { order.indexOf(it).let { i -> if (i < 0) order.size else i } }
    return if (names.size > shown) "${names.take(shown - 1).joinToString(", ")} +${names.size - shown + 1}" else names.joinToString(", ")
}

internal class WordMatcher(words: List<String>) {
    private val regex = words.takeIf { it.isNotEmpty() }
        ?.sortedByDescending { it.length }
        ?.joinToString("|", "(?<![\\p{L}\\p{N}])(?:", ")(?![\\p{L}\\p{N}])") { Regex.escape(it) }
        ?.toRegex(RegexOption.IGNORE_CASE)

    fun matches(text: String) = regex?.containsMatchIn(text) == true

    fun ranges(text: String): List<IntRange> = regex?.findAll(text)?.map { it.range }?.toList().orEmpty()
}

internal fun matches(listing: Listing, query: String) =
    listOf(listing.title, listing.description, listing.leader).any { it.contains(query, ignoreCase = true) } || codeMatches(listing.code, query)

private fun codeMatches(code: String, query: String) =
    code.startsWith(query, ignoreCase = true) && code.getOrNull(query.length)?.isDigit() != true && query.any { it.isDigit() }
