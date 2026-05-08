package com.bgiptv.app.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [Index("canonicalName"), Index("groupTag"), Index("isFavorite")],
)
data class ChannelEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val canonicalName: String,       // normalized name (stripped HD/4K/FR|/etc.)
    val groupId: String,             // category_id from provider (canonical group)
    val groupTag: String,            // our clean group (FR_SPORT, FR_GENERAL, etc.)
    val countryCode: String?,        // "FR", "GB", "ES"...
    val iconUrl: String?,
    val epgChannelId: String?,
    val quality: String,             // "SD", "HD", "FHD", "4K"
    val hasCatchup: Boolean,
    val catchupDays: Int,
    val lcn: Int?,                   // user-assigned channel number
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val userCodecOverride: String?,  // user-forced codec for this channel
    val userQualityOverride: String?, // user-forced quality variant
    val addedAt: Long,
    val lastWatchedAt: Long?,
)
