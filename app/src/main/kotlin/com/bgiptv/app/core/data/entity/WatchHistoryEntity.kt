package com.bgiptv.app.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    indices = [Index("channelId"), Index("watchedAt")],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Int,
    val watchedAt: Long,
    val durationSeconds: Long,
    val dayOfWeek: Int,    // 1=Mon...7=Sun (for habit learning)
    val hourOfDay: Int,    // 0-23
)
