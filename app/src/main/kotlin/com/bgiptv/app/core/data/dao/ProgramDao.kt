package com.bgiptv.app.core.data.dao

import androidx.room.*
import com.bgiptv.app.core.data.entity.ProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {

    @Query("""
        SELECT * FROM programs
        WHERE channelId = :channelId
        AND stopTimestamp > :now
        ORDER BY startTimestamp ASC
        LIMIT 4
    """)
    suspend fun getUpcomingForChannel(channelId: Int, now: Long): List<ProgramEntity>

    @Query("""
        SELECT * FROM programs
        WHERE channelId = :channelId
        AND startTimestamp <= :now
        AND stopTimestamp > :now
        LIMIT 1
    """)
    suspend fun getCurrentForChannel(channelId: Int, now: Long): ProgramEntity?

    @Query("""
        SELECT p.* FROM programs p
        WHERE p.startTimestamp <= :now
        AND p.stopTimestamp > :now
        AND (p.title LIKE '%' || :query || '%' OR p.description LIKE '%' || :query || '%')
        LIMIT 10
    """)
    suspend fun searchCurrentPrograms(query: String, now: Long): List<ProgramEntity>

    @Query("""
        SELECT p.* FROM programs p
        WHERE p.startTimestamp > :now
        AND p.startTimestamp < :until
        AND (p.title LIKE '%' || :query || '%' OR p.description LIKE '%' || :query || '%')
        LIMIT 10
    """)
    suspend fun searchUpcomingPrograms(query: String, now: Long, until: Long): List<ProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<ProgramEntity>)

    @Query("DELETE FROM programs WHERE stopTimestamp < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM programs WHERE channelId = :channelId")
    suspend fun deleteForChannel(channelId: Int)
}
