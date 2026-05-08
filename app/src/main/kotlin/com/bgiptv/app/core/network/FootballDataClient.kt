package com.bgiptv.app.core.network

import com.bgiptv.app.core.data.dao.SportEventDao
import com.bgiptv.app.core.data.entity.SportEventEntity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// football-data.org free tier — 10 req/min, no API key needed for basic endpoints
private const val BASE_URL = "https://api.football-data.org/v4"

// Competitions couverts en priorité FR
private val COMPETITIONS = mapOf(
    "FL1" to "Ligue 1",
    "CL"  to "Champions League",
    "EL"  to "Europa League",
    "PL"  to "Premier League",
    "PD"  to "La Liga",
    "SA"  to "Serie A",
)

@JsonClass(generateAdapter = true)
data class FdMatch(
    @Json(name = "id") val id: Int,
    @Json(name = "utcDate") val utcDate: String,
    @Json(name = "status") val status: String, // "SCHEDULED", "IN_PLAY", "PAUSED", "FINISHED"
    @Json(name = "minute") val minute: Int?,
    @Json(name = "homeTeam") val homeTeam: FdTeam,
    @Json(name = "awayTeam") val awayTeam: FdTeam,
    @Json(name = "score") val score: FdScore,
    @Json(name = "competition") val competition: FdCompetition,
)

@JsonClass(generateAdapter = true)
data class FdTeam(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "shortName") val shortName: String?,
)

@JsonClass(generateAdapter = true)
data class FdScore(
    @Json(name = "home") val home: Int?,
    @Json(name = "away") val away: Int?,
)

@JsonClass(generateAdapter = true)
data class FdCompetition(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "code") val code: String,
)

@JsonClass(generateAdapter = true)
data class FdMatchesResponse(
    @Json(name = "matches") val matches: List<FdMatch>,
)

@Singleton
class FootballDataClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val sportEventDao: SportEventDao,
) {
    private val adapter = moshi.adapter(FdMatchesResponse::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.of("Europe/Paris"))

    suspend fun refreshTodayMatches() = withContext(Dispatchers.IO) {
        val today = dateFormatter.format(Instant.now())
        val tomorrow = dateFormatter.format(Instant.now().plusSeconds(86400))

        COMPETITIONS.keys.forEach { code ->
            runCatching {
                fetchMatches(code, today, tomorrow)
            }.onSuccess { matches ->
                val entities = matches.map { it.toEntity() }
                sportEventDao.insertAll(entities)
            }
            // Rate limit: free tier = 10 req/min
            kotlinx.coroutines.delay(7_000)
        }

        // Clean old events (> 24h ago)
        sportEventDao.deleteBefore(System.currentTimeMillis() - 86_400_000)
    }

    suspend fun refreshLiveScores() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        COMPETITIONS.keys.forEach { code ->
            runCatching {
                fetchLiveMatches(code)
            }.onSuccess { matches ->
                matches.forEach { match ->
                    if (match.status in listOf("IN_PLAY", "PAUSED", "HALFTIME")) {
                        sportEventDao.updateLiveScore(
                            id = "football-data-${match.id}",
                            home = match.score.home ?: 0,
                            away = match.score.away ?: 0,
                            minute = match.minute ?: 0,
                            status = match.status.toInternalStatus(),
                            excitement = computeExcitement(match),
                            updatedAt = now,
                        )
                    }
                }
            }
            kotlinx.coroutines.delay(7_000)
        }
    }

    private fun fetchMatches(competitionCode: String, dateFrom: String, dateTo: String): List<FdMatch> {
        val request = Request.Builder()
            .url("$BASE_URL/competitions/$competitionCode/matches?dateFrom=$dateFrom&dateTo=$dateTo")
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            adapter.fromJson(response.body?.string() ?: "")?.matches ?: emptyList()
        }
    }

    private fun fetchLiveMatches(competitionCode: String): List<FdMatch> {
        val request = Request.Builder()
            .url("$BASE_URL/competitions/$competitionCode/matches?status=IN_PLAY,PAUSED")
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            adapter.fromJson(response.body?.string() ?: "")?.matches ?: emptyList()
        }
    }

    private fun FdMatch.toEntity(): SportEventEntity {
        val startInstant = Instant.parse(utcDate)
        val durationEstimated = 105 * 60 // 105 min average match
        return SportEventEntity(
            id = "football-data-$id",
            sport = "football",
            competition = competition.name,
            homeTeam = homeTeam.shortName ?: homeTeam.name,
            awayTeam = awayTeam.shortName ?: awayTeam.name,
            homeScore = score.home,
            awayScore = score.away,
            minute = minute,
            status = status.toInternalStatus(),
            startTimestamp = startInstant.toEpochMilli(),
            stopTimestamp = startInstant.toEpochMilli() + durationEstimated * 1000L,
            channelIds = "[]",           // matched later by EventMatcher
            broadcasterHints = broadcasterHintsFor(competition.code),
            storyline = null,            // enriched separately
            excitementScore = computeExcitement(this),
            isFollowed = false,          // updated by user prefs
            source = "football-data",
            externalId = id.toString(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun computeExcitement(match: FdMatch): Float {
        if (match.status !in listOf("IN_PLAY", "PAUSED")) return 0f
        val minute = match.minute ?: 0
        val homeScore = match.score.home ?: 0
        val awayScore = match.score.away ?: 0
        val scoreDiff = Math.abs(homeScore - awayScore)
        val totalGoals = homeScore + awayScore

        // Factors: time pressure (late game = more exciting), close score, goals scored
        val timePressure = (minute / 90f).coerceIn(0f, 1f)
        val closeScore = if (scoreDiff <= 1) 1f else if (scoreDiff == 2) 0.5f else 0.2f
        val goalsBonus = (totalGoals * 0.15f).coerceIn(0f, 0.4f)

        return ((timePressure * 0.4f + closeScore * 0.4f + goalsBonus) * 1.2f).coerceIn(0f, 1f)
    }

    private fun String.toInternalStatus(): String = when (this) {
        "SCHEDULED", "TIMED" -> "NS"
        "IN_PLAY" -> "1H"
        "PAUSED", "HALFTIME" -> "HT"
        "FINISHED" -> "FT"
        else -> "NS"
    }

    private fun broadcasterHintsFor(competitionCode: String): String {
        val hints = when (competitionCode) {
            "FL1" -> listOf("Canal+ Foot", "DAZN", "Ligue1+", "beIN Sports")
            "CL"  -> listOf("Canal+", "beIN Sports")
            "EL"  -> listOf("Canal+", "beIN Sports")
            "PL"  -> listOf("Canal+")
            "PD"  -> listOf("beIN Sports")
            "SA"  -> listOf("beIN Sports")
            else  -> emptyList()
        }
        return "[${hints.joinToString(",") { "\"$it\"" }}]"
    }
}
