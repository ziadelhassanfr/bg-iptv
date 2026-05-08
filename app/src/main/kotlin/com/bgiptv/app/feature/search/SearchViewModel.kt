package com.bgiptv.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgiptv.app.core.data.dao.ChannelDao
import com.bgiptv.app.core.data.dao.ProgramDao
import com.bgiptv.app.core.data.dao.SportEventDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val programDao: ProgramDao,
    private val sportEventDao: SportEventDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)

    init {
        viewModelScope.launch {
            _query
                .debounce(200)
                .filter { it.length >= 2 }
                .collectLatest { query ->
                    _uiState.update { it.copy(isLoading = true) }
                    val results = search(query)
                    _uiState.update { it.copy(results = results, isLoading = false) }
                }
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
        _uiState.update { it.copy(query = query) }
        if (query.length < 2) {
            _uiState.update { it.copy(results = emptyList()) }
        }
    }

    private suspend fun search(query: String): List<SearchResult> {
        val now = System.currentTimeMillis()
        val twoHoursFromNow = now + 2 * 60 * 60 * 1000L
        val results = mutableListOf<SearchResult>()

        // Live sport events
        val liveEvents = sportEventDao.searchEvents(query, now - 120 * 60 * 1000L)
            .filter { it.status in listOf("1H", "2H", "HT", "live") }
        results += liveEvents.map { event ->
            SearchResult.LiveEvent(
                eventId = event.id,
                title = "${event.homeTeam ?: ""} ${event.homeScore ?: ""} - ${event.awayScore ?: ""} ${event.awayTeam ?: ""}".trim(),
                competition = event.competition,
                channelName = "Voir diffusion",
                minute = event.minute,
            )
        }

        // Upcoming events
        val upcoming = sportEventDao.searchEvents(query, now)
            .filter { it.status == "NS" && it.startTimestamp < twoHoursFromNow }
        results += upcoming.map { event ->
            SearchResult.UpcomingEvent(
                eventId = event.id,
                title = "${event.homeTeam ?: ""} vs ${event.awayTeam ?: ""}".trim(),
                competition = event.competition,
                channelName = "Voir diffusion",
                startTime = timeFormat.format(Date(event.startTimestamp)),
            )
        }

        // Channels
        val channels = channelDao.searchByName(query)
        val currentPrograms = channels.associateWith { ch ->
            programDao.getCurrentForChannel(ch.streamId, now)
        }
        results += channels.map { ch ->
            SearchResult.Channel(
                streamId = ch.streamId,
                name = ch.canonicalName,
                currentProgram = currentPrograms[ch]?.title,
            )
        }

        // EPG programs
        val currentPrograms2 = programDao.searchCurrentPrograms(query, now)
        val upcomingPrograms = programDao.searchUpcomingPrograms(query, now, twoHoursFromNow)
        (currentPrograms2 + upcomingPrograms).distinctBy { it.id }.forEach { prog ->
            val channel = channelDao.getById(prog.channelId)
            results += SearchResult.EpgProgram(
                channelId = prog.channelId,
                title = prog.title,
                channelName = channel?.canonicalName ?: "Chaîne ${prog.channelId}",
                startTime = timeFormat.format(Date(prog.startTimestamp)),
            )
        }

        return results.take(30)
    }
}
