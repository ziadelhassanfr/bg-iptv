package com.bgiptv.app.core.data.dao

import androidx.room.*
import com.bgiptv.app.core.data.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("""
        SELECT channelId, MAX(watchedAt) as watchedAt, SUM(durationSeconds) as durationSeconds,
               dayOfWeek, hourOfDay, 0 as id
        FROM watch_history
        GROUP BY channelId
        ORDER BY MAX(watchedAt) DESC
        LIMIT 10
    """)
    fun observeRecentChannels(): Flow<List<WatchHistoryEntity>>

    @Query("""
        SELECT channelId, COUNT(*) as viewCount
        FROM watch_history
        WHERE dayOfWeek = :dayOfWeek AND hourOfDay BETWEEN :hourStart AND :hourEnd
        GROUP BY channelId
        ORDER BY viewCount DESC
        LIMIT 5
    """)
    suspend fun getHabitsForTimeSlot(dayOfWeek: Int, hourStart: Int, hourEnd: Int): List<ChannelHabit>

    @Insert
    suspend fun insert(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE watchedAt < :before")
    suspend fun deleteBefore(before: Long)
}

data class ChannelHabit(
    val channelId: Int,
    val viewCount: Int,
)
