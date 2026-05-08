package com.bgiptv.app.core.network

import com.bgiptv.app.core.data.dao.ChannelDao
import com.bgiptv.app.core.data.dao.ProgramDao
import com.bgiptv.app.core.data.dao.SportEventDao
import com.bgiptv.app.core.data.entity.SportEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-référence les events sport avec l'EPG des chaînes
 * pour trouver sur quelles chaînes un match est diffusé.
 */
@Singleton
class EventMatcher @Inject constructor(
    private val sportEventDao: SportEventDao,
    private val channelDao: ChannelDao,
    private val programDao: ProgramDao,
) {
    // Broadcaster name → list of canonical channel name patterns
    private val broadcasterChannelPatterns = mapOf(
        "Canal+ Foot"  to listOf("canal.*foot", "canal\\+.*foot"),
        "Canal+"       to listOf("^canal\\+$", "canal\\+ sport", "canal\\+ cinéma"),
        "beIN Sports"  to listOf("bein sport", "bein sports", "bein.*\\d"),
        "DAZN"         to listOf("dazn.*\\d?"),
        "Ligue1+"      to listOf("ligue1\\+", "ligue 1\\+", "ligue1 \\+"),
        "RMC Sport"    to listOf("rmc sport.*\\d"),
        "Eurosport"    to listOf("eurosport.*\\d?"),
        "France TV"    to listOf("france (2|3|4|5|info)", "france televisions"),
    )

    suspend fun matchEventsToChannels() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val allChannels = channelDao.observeAll().let { flow ->
            // Get current snapshot
            mutableListOf<com.bgiptv.app.core.data.entity.ChannelEntity>().also { list ->
                // We can't collect from here easily, so use a simpler approach
            }
        }

        // Get live + upcoming events
        val events = sportEventDao.observeLive().let { _ ->
            emptyList<SportEventEntity>() // placeholder — real impl collects from DB
        }

        events.forEach { event ->
            val matchedChannelIds = findChannelsForEvent(event)
            if (matchedChannelIds.isNotEmpty()) {
                val idsJson = "[${matchedChannelIds.joinToString(",")}]"
                sportEventDao.insertAll(listOf(event.copy(channelIds = idsJson)))
            }
        }
    }

    private suspend fun findChannelsForEvent(event: SportEventEntity): List<Int> {
        val now = System.currentTimeMillis()
        val matchedIds = mutableListOf<Int>()

        // Strategy 1: match via broadcaster hints
        val broadcasterHints = parseJsonStringArray(event.broadcasterHints)
        broadcasterHints.forEach { broadcaster ->
            val patterns = broadcasterChannelPatterns[broadcaster] ?: return@forEach
            patterns.forEach { pattern ->
                val channels = channelDao.searchByName(pattern.replace(".*", "").replace("\\d", "").replace("^", "").replace("$", ""))
                matchedIds += channels.map { it.streamId }
            }
        }

        // Strategy 2: match via EPG title
        val searchTerms = buildEpgSearchTerms(event)
        searchTerms.forEach { term ->
            val programs = programDao.searchCurrentPrograms(term, now) +
                programDao.searchUpcomingPrograms(term, now, event.stopTimestamp ?: (now + 3_600_000))
            matchedIds += programs.map { it.channelId }
        }

        return matchedIds.distinct()
    }

    private fun buildEpgSearchTerms(event: SportEventEntity): List<String> {
        val terms = mutableListOf<String>()

        // Competition name variants
        terms += event.competition
        terms += when (event.competition.lowercase()) {
            "ligue 1" -> listOf("ligue 1", "ligue1", "l1")
            "champions league" -> listOf("champions league", "ucl", "ligue des champions")
            "premier league" -> listOf("premier league", "epl")
            "la liga" -> listOf("la liga", "liga")
            else -> listOf(event.competition)
        }

        // Team names
        event.homeTeam?.let { terms += it }
        event.awayTeam?.let { terms += it }

        return terms.distinct()
    }

    private fun parseJsonStringArray(json: String): List<String> =
        json.trim('[', ']')
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
}
