package com.bgiptv.app.core.player

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val currentStreamId: Int? = null,
    val profile: BufferProfile = BufferProfile.ZAPPING,
)

@Singleton
class BgPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private var player: ExoPlayer? = null
    private var preBufferPlayer: ExoPlayer? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    fun getOrCreatePlayer(profile: BufferProfile = BufferProfile.ZAPPING): ExoPlayer {
        if (player == null || _state.value.profile != profile) {
            player?.release()
            player = buildPlayer(profile)
        }
        return player!!
    }

    fun play(streamUrl: String, streamId: Int, profile: BufferProfile) {
        val p = getOrCreatePlayer(profile)
        p.setMediaItem(MediaItem.fromUri(streamUrl))
        p.prepare()
        p.playWhenReady = true
        _state.value = _state.value.copy(
            currentStreamId = streamId,
            profile = profile,
            hasError = false,
            errorMessage = null,
        )
    }

    // Pre-buffer a neighbor channel silently (called during zapping navigation)
    fun preBuffer(streamUrl: String) {
        preBufferPlayer?.release()
        preBufferPlayer = buildPlayer(BufferProfile.ZAPPING).also { p ->
            p.setMediaItem(MediaItem.fromUri(streamUrl))
            p.prepare()
            p.playWhenReady = false
            p.volume = 0f
        }
    }

    // Instantly swap to pre-buffered player if it matches
    fun swapToPreBuffer(streamUrl: String, streamId: Int, profile: BufferProfile): Boolean {
        val pre = preBufferPlayer ?: return false
        val preItem = pre.currentMediaItem?.localConfiguration?.uri?.toString()
        if (preItem != streamUrl) return false

        player?.release()
        player = pre.also { it.volume = 1f; it.playWhenReady = true }
        preBufferPlayer = null
        _state.value = _state.value.copy(currentStreamId = streamId, profile = profile)
        return true
    }

    fun attachSurface(surfaceView: SurfaceView) {
        player?.setVideoSurfaceView(surfaceView)
    }

    fun release() {
        player?.release()
        player = null
        preBufferPlayer?.release()
        preBufferPlayer = null
    }

    private fun buildPlayer(profile: BufferProfile): ExoPlayer {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguage("fr"))
        }

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(profile.toLoadControl())
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _state.value = _state.value.copy(
                            isPlaying = p.isPlaying,
                            isBuffering = state == Player.STATE_BUFFERING,
                        )
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        _state.value = _state.value.copy(
                            hasError = true,
                            errorMessage = error.message,
                        )
                    }
                })
            }
    }
}
