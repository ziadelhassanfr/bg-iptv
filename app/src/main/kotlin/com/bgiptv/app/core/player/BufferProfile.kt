package com.bgiptv.app.core.player

import androidx.media3.exoplayer.DefaultLoadControl

enum class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
) {
    SPORT(
        minBufferMs = 8_000,
        maxBufferMs = 30_000,
        bufferForPlaybackMs = 2_000,
        bufferForPlaybackAfterRebufferMs = 4_000,
    ),
    FILM(
        minBufferMs = 30_000,
        maxBufferMs = 120_000,
        bufferForPlaybackMs = 5_000,
        bufferForPlaybackAfterRebufferMs = 15_000,
    ),
    ZAPPING(
        minBufferMs = 15_000,
        maxBufferMs = 50_000,
        bufferForPlaybackMs = 2_500,
        bufferForPlaybackAfterRebufferMs = 5_000,
    );

    fun toLoadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
}
