package com.benji.partybell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PartyState {
    private const val FEED_SIZE = 200

    private val partyFlow = MutableStateFlow(Party())
    private val feedFlow = MutableStateFlow<List<FeedItem>>(emptyList())
    private val statusFlow = MutableStateFlow(Status.Off)

    val party: StateFlow<Party> = partyFlow.asStateFlow()
    val feed: StateFlow<List<FeedItem>> = feedFlow.asStateFlow()
    val status: StateFlow<Status> = statusFlow.asStateFlow()

    fun apply(incoming: Incoming) {
        when (incoming) {
            is PartyUpdate -> partyFlow.value = incoming.party
            is History -> feedFlow.value = incoming.items.takeLast(FEED_SIZE)
            is Live -> feedFlow.value = (feedFlow.value + incoming.item).takeLast(FEED_SIZE)
        }
    }

    fun setStatus(status: Status) {
        statusFlow.value = status
    }
}
