package com.bgiptv.app.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programs",
    indices = [Index("channelId"), Index("startTimestamp"), Index("stopTimestamp")],
)
data class ProgramEntity(
    @PrimaryKey val id: String,            // epg listing id
    val channelId: Int,
    val epgChannelId: String,
    val title: String,
    val description: String?,
    val startTimestamp: Long,
    val stopTimestamp: Long,
    val categoryHint: String?,             // "sport", "movie", "news"...
)
