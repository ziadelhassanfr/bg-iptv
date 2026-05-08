package com.bgiptv.app.feature.player

import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgiptv.app.core.data.XtreamRepository
import com.bgiptv.app.core.data.dao.ChannelDao
import com.bgiptv.app.core.data.dao.ProgramDao
import com.bgiptv.app.core.data.dao.WatchHistoryDao
import com.bgiptv.app.core.data.entity.WatchHistoryEntity
import com.bgiptv.app.core.player.BgPlayerManager
import com.bgiptv.app.core.player.BufferProfile
import com.bgiptv.app.core.security.CredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Calendar
import javax.inject.Inject

data class PlayerUiState(
    val channelName: String = "",
    val currentProgram: String? = null,
    val nextProgram: String? = null,
    val quality: String = "HD",
    val isBuffering: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val currentStreamId: Int? = null,
    val liveScoreAlert: ScoreAlert? = null,
    val currentGroupChannelIds: List<Int> = emptyList(),
    val currentIndexInGroup: Int = 0,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: BgPlayerManager,
    private val channelDao: ChannelDao,
    private val programDao: ProgramDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val credentialsManager: CredentialsManager,
    private val xtreamRepository: XtreamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var watchStartTime = 0L
    private var watchJob: Job? = null

    fun playChannel(streamId: Int) {
        viewModelScope.launch {
            val channel = channelDao.getById(streamId) ?: return@launch
            val credentials = credentialsManager.getCredentials() ?: return@launch

            val streamUrl = xtreamRepository.buildStreamUrl(credentials, streamId)
            val profile = inferProfile(channel.groupTag)

            // Try pre-buffer swap first (instant zap)
            val swapped = playerManager.swapToPreBuffer(streamUrl, streamId, profile)
            if (!swapped) {
                playerManager.play(streamUrl, streamId, profile)
            }

            // Update channel last watched
            channelDao.updateLastWatched(streamId, System.currentTimeMillis())

            // Load EPG info
            val now = System.currentTimeMillis()
            val current = programDao.getCurrentForChannel(streamId, now)
            val upcoming = programDao.getUpcomingForChannel(streamId, now).firstOrNull()

            _uiState.update { it.copy(
                channelName = channel.canonicalName.replaceFirstChar { c -> c.uppercase() },
                currentProgram = current?.title,
                nextProgram = upcoming?.title,
                quality = channel.quality,
                currentStreamId = streamId,
                hasError = false,
            ) }

            // Start watch time tracking
            recordWatchStart(streamId)

            // Pre-buffer neighbor channels
            preBufferNeighbors(streamId)
        }

        // Observe player state
        viewModelScope.launch {
            playerManager.state.collect { state ->
                _uiState.update { it.copy(
                    isBuffering = state.isBuffering,
                    hasError = state.hasError,
                    errorMessage = state.errorMessage,
                ) }
            }
        }
    }

    fun zapUp() {
        val state = _uiState.value
        val ids = state.currentGroupChannelIds
        if (ids.isEmpty()) return
        val nextIndex = (state.currentIndexInGroup - 1 + ids.size) % ids.size
        _uiState.update { it.copy(currentIndexInGroup = nextIndex) }
        playChannel(ids[nextIndex])
    }

    fun zapDown() {
        val state = _uiState.value
        val ids = state.currentGroupChannelIds
        if (ids.isEmpty()) return
        val nextIndex = (state.currentIndexInGroup + 1) % ids.size
        _uiState.update { it.copy(currentIndexInGroup = nextIndex) }
        playChannel(ids[nextIndex])
    }

    fun prevGroup() { /* TODO: navigate to previous group in browser */ }
    fun nextGroup() { /* TODO: navigate to next group in browser */ }

    fun attachSurface(surfaceView: SurfaceView) {
        playerManager.attachSurface(surfaceView)
    }

    fun retry() {
        val streamId = _uiState.value.currentStreamId ?: return
        playChannel(streamId)
    }

    fun switchToAlert(alert: ScoreAlert) {
        playChannel(alert.channelId)
        dismissAlert()
    }

    fun dismissAlert() {
        _uiState.update { it.copy(liveScoreAlert = null) }
    }

    private fun preBufferNeighbors(currentStreamId: Int) {
        viewModelScope.launch {
            val ids = _uiState.value.currentGroupChannelIds
            val currentIdx = ids.indexOf(currentStreamId)
            if (currentIdx < 0) return@launch

            val nextId = ids.getOrNull(currentIdx + 1) ?: return@launch
            val credentials = credentialsManager.getCredentials() ?: return@launch
            val nextUrl = xtreamRepository.buildStreamUrl(credentials, nextId)
            playerManager.preBuffer(nextUrl)
        }
    }

    private fun recordWatchStart(streamId: Int) {
        watchJob?.cancel()
        watchStartTime = System.currentTimeMillis()
        watchJob = viewModelScope.launch {
            delay(10_000) // Only record if watched > 10s
            val duration = (System.currentTimeMillis() - watchStartTime) / 1000
            val cal = Calendar.getInstance()
            watchHistoryDao.insert(WatchHistoryEntity(
                channelId = streamId,
                watchedAt = watchStartTime,
                durationSeconds = duration,
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
            ))
        }
    }

    private fun inferProfile(groupTag: String): BufferProfile = when (groupTag) {
        "FOOT", "F1", "NBA", "TENNIS", "SPORT", "COMBAT" -> BufferProfile.SPORT
        "CINEMA" -> BufferProfile.FILM
        else -> BufferProfile.ZAPPING
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
