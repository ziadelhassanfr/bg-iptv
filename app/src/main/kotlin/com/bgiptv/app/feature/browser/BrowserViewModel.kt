package com.bgiptv.app.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgiptv.app.core.data.XtreamRepository
import com.bgiptv.app.core.data.dao.ChannelDao
import com.bgiptv.app.core.data.dao.SportEventDao
import com.bgiptv.app.core.data.dao.WatchHistoryDao
import com.bgiptv.app.core.data.entity.ChannelEntity
import com.bgiptv.app.core.data.entity.SportEventEntity
import com.bgiptv.app.core.security.CredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class BrowserGroup(
    val tag: String,
    val label: String,
    val emoji: String,
    val channelCount: Int,
    val hasLiveEvent: Boolean = false,
)

sealed class BrowserContent {
    data class Channels(val channels: List<ChannelEntity>) : BrowserContent()
    data class Events(val events: List<SportEventEntity>) : BrowserContent()
    data object Empty : BrowserContent()
}

data class BrowserUiState(
    val groups: List<BrowserGroup> = emptyList(),
    val selectedGroupIndex: Int = 0,
    val rightContent: BrowserContent = BrowserContent.Empty,
    val currentChannelId: Int? = null,
    val currentProgramTitle: String? = null,
    val isOverlay: Boolean = false,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importedChannels: Int = 0,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val sportEventDao: SportEventDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val credentialsManager: CredentialsManager,
    private val xtreamRepository: XtreamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val now get() = Instant.now().epochSecond * 1000L
    private val twoHoursFromNow get() = now + 2 * 60 * 60 * 1000L

    init {
        viewModelScope.launch {
            // Auto-import if credentials exist but no channels yet
            val count = channelDao.count()
            if (count == 0 && credentialsManager.hasCredentials()) {
                triggerImport()
            }
        }
        observeGroups()
    }

    private fun triggerImport() {
        val creds = credentialsManager.getCredentials() ?: return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            xtreamRepository.importAll(creds) { progress ->
                _uiState.update {
                    it.copy(
                        importedChannels = progress.channels,
                        isImporting = !progress.isComplete,
                    )
                }
            }
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            combine(
                sportEventDao.observeLive(),
                sportEventDao.observeUpcoming(now, twoHoursFromNow),
                channelDao.observeAllGroupTags(),
                channelDao.observeAllCountryCodes(),
                channelDao.observeRecent(),
            ) { liveEvents, upcomingEvents, groupTags, countries, recent ->
                buildGroups(liveEvents, upcomingEvents, groupTags, countries, recent)
            }.collect { groups ->
                _uiState.update { it.copy(groups = groups, isLoading = false) }
                loadGroupContent(_uiState.value.selectedGroupIndex)
            }
        }
    }

    private fun buildGroups(
        liveEvents: List<SportEventEntity>,
        upcomingEvents: List<SportEventEntity>,
        groupTags: List<String>,
        countries: List<String>,
        recent: List<ChannelEntity>,
    ): List<BrowserGroup> {
        val groups = mutableListOf<BrowserGroup>()

        if (liveEvents.isNotEmpty()) {
            groups += BrowserGroup("live", "EN DIRECT", "🔴", liveEvents.size, true)
        }
        if (upcomingEvents.isNotEmpty()) {
            groups += BrowserGroup("upcoming", "BIENTÔT", "⏰", upcomingEvents.size)
        }
        if (recent.isNotEmpty()) {
            groups += BrowserGroup("recent", "REPRENDRE", "⏯", recent.size)
        }

        groupTags.filter { it.endsWith("_SPORT") || it == "FOOT" || it == "F1" || it == "NBA" }
            .forEach { tag ->
                groups += BrowserGroup(tag, formatGroupLabel(tag), sportEmoji(tag), 0)
            }

        countries.take(8).forEach { code ->
            groups += BrowserGroup("country_$code", code, countryFlag(code), 0)
        }

        listOf("NEWS", "CINEMA", "JEUNESSE", "DOCS", "MUSIQUE").forEach { tag ->
            if (groupTags.contains(tag)) {
                groups += BrowserGroup(tag, formatGroupLabel(tag), themeEmoji(tag), 0)
            }
        }

        groups += BrowserGroup("favorites", "Favoris", "⭐", 0)
        groups += BrowserGroup("vod_movies", "Films VOD", "🎬", 0)
        groups += BrowserGroup("vod_series", "Séries VOD", "📺", 0)

        return groups
    }

    fun selectGroup(index: Int) {
        _uiState.update { it.copy(selectedGroupIndex = index) }
        loadGroupContent(index)
    }

    private fun loadGroupContent(index: Int) {
        val groups = _uiState.value.groups
        if (index >= groups.size) return
        val group = groups[index]

        viewModelScope.launch {
            when (group.tag) {
                "live" -> sportEventDao.observeLive().first().let { events ->
                    _uiState.update { it.copy(rightContent = BrowserContent.Events(events)) }
                }
                "upcoming" -> sportEventDao.observeUpcoming(now, twoHoursFromNow).first().let { events ->
                    _uiState.update { it.copy(rightContent = BrowserContent.Events(events)) }
                }
                "recent" -> channelDao.observeRecent().first().let { channels ->
                    _uiState.update { it.copy(rightContent = BrowserContent.Channels(channels)) }
                }
                "favorites" -> channelDao.observeFavorites().first().let { channels ->
                    _uiState.update { it.copy(rightContent = BrowserContent.Channels(channels)) }
                }
                else -> {
                    val channels = when {
                        group.tag.startsWith("country_") ->
                            channelDao.observeByCountry(group.tag.removePrefix("country_")).first()
                        else ->
                            channelDao.observeByGroup(group.tag).first()
                    }
                    _uiState.update { it.copy(rightContent = BrowserContent.Channels(channels)) }
                }
            }
        }
    }

    fun toggleFavorite(streamId: Int, current: Boolean) {
        viewModelScope.launch {
            channelDao.setFavorite(streamId, !current)
        }
    }

    fun setOverlay(isOverlay: Boolean) {
        _uiState.update { it.copy(isOverlay = isOverlay) }
    }

    private fun formatGroupLabel(tag: String): String = when (tag) {
        "FOOT" -> "Foot"
        "F1" -> "F1"
        "NBA" -> "NBA"
        "FR_SPORT" -> "Sport FR"
        "NEWS" -> "Info"
        "CINEMA" -> "Cinéma"
        "JEUNESSE" -> "Jeunesse"
        "DOCS" -> "Docs"
        "MUSIQUE" -> "Musique"
        else -> tag.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun sportEmoji(tag: String) = when (tag) {
        "FOOT" -> "⚽"
        "F1" -> "🏎"
        "NBA" -> "🏀"
        "TENNIS" -> "🎾"
        "COMBAT" -> "🥊"
        else -> "🏆"
    }

    private fun themeEmoji(tag: String) = when (tag) {
        "NEWS" -> "📺"
        "CINEMA" -> "🎬"
        "JEUNESSE" -> "👶"
        "DOCS" -> "📚"
        "MUSIQUE" -> "🎵"
        else -> "📺"
    }

    private fun countryFlag(code: String): String {
        if (code.length != 2) return "🌍"
        val offset = 0x1F1E6
        val firstChar = code[0].uppercaseChar().code - 'A'.code + offset
        val secondChar = code[1].uppercaseChar().code - 'A'.code + offset
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}
