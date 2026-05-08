package com.bgiptv.app.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sport_events",
    indices = [Index("startTimestamp"), Index("sport"), Index("status")],
)
data class SportEventEntity(
    @PrimaryKey val id: String,
    val sport: String,                    // "football", "nba", "f1", "tennis"
    val competition: String,              // "Ligue 1", "Champions League"
    val homeTeam: String?,
    val awayTeam: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val minute: Int?,
    val status: String,                   // "NS", "1H", "HT", "2H", "FT", "live"
    val startTimestamp: Long,
    val stopTimestamp: Long?,
    val channelIds: String,               // JSON array of matched channel stream IDs
    val broadcasterHints: String,         // JSON array of broadcaster names (Canal+, beIN...)
    val storyline: String?,               // e.g. "Mbappé peut atteindre 250 buts"
    val excitementScore: Float,           // 0.0-1.0, computed from score/minute/stakes
    val isFollowed: Boolean,              // user follows this team/competition
    val source: String,                   // "football-data", "epg-match", "thesportsdb"
    val externalId: String?,              // ID dans l'API source
    val updatedAt: Long,
)
