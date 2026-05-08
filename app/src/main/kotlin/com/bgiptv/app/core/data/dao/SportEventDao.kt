package com.bgiptv.app.core.data.dao

import androidx.room.*
import com.bgiptv.app.core.data.entity.SportEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SportEventDao {

    @Query("""
        SELECT * FROM sport_events
        WHERE status IN ('1H', '2H', 'HT', 'live')
        ORDER BY excitementScore DESC, startTimestamp ASC
    """)
    fun observeLive(): Flow<List<SportEventEntity>>

    @Query("""
        SELECT * FROM sport_events
        WHERE status = 'NS'
        AND startTimestamp > :now
        AND startTimestamp < :until
        ORDER BY isFollowed DESC, startTimestamp ASC
    """)
    fun observeUpcoming(now: Long, until: Long): Flow<List<SportEventEntity>>

    @Query("""
        SELECT * FROM sport_events
        WHERE startTimestamp >= :dayStart
        AND startTimestamp < :dayEnd
        ORDER BY startTimestamp ASC
    """)
    fun observeForDay(dayStart: Long, dayEnd: Long): Flow<List<SportEventEntity>>

    @Query("""
        SELECT * FROM sport_events
        WHERE (homeTeam LIKE '%' || :query || '%' OR awayTeam LIKE '%' || :query || '%'
               OR competition LIKE '%' || :query || '%')
        AND startTimestamp > :now
        ORDER BY startTimestamp ASC
        LIMIT 10
    """)
    suspend fun searchEvents(query: String, now: Long): List<SportEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<SportEventEntity>)

    @Query("UPDATE sport_events SET homeScore = :home, awayScore = :away, minute = :minute, status = :status, excitementScore = :excitement, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLiveScore(id: String, home: Int, away: Int, minute: Int, status: String, excitement: Float, updatedAt: Long)

    @Query("DELETE FROM sport_events WHERE startTimestamp < :before")
    suspend fun deleteBefore(before: Long)
}
