package com.bgiptv.app.core.data.dao

import androidx.room.*
import com.bgiptv.app.core.data.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels WHERE isHidden = 0 ORDER BY lcn ASC, name ASC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupTag = :groupTag AND isHidden = 0 ORDER BY lcn ASC, name ASC")
    fun observeByGroup(groupTag: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND isHidden = 0 ORDER BY lcn ASC, name ASC")
    fun observeFavorites(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE countryCode = :code AND isHidden = 0 ORDER BY name ASC")
    fun observeByCountry(code: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE streamId = :id")
    suspend fun getById(id: Int): ChannelEntity?

    @Query("""
        SELECT * FROM channels
        WHERE canonicalName LIKE '%' || :query || '%'
        AND isHidden = 0
        LIMIT 20
    """)
    suspend fun searchByName(query: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE lastWatchedAt IS NOT NULL ORDER BY lastWatchedAt DESC LIMIT 10")
    fun observeRecent(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun setFavorite(streamId: Int, isFavorite: Boolean)

    @Query("UPDATE channels SET lastWatchedAt = :timestamp WHERE streamId = :streamId")
    suspend fun updateLastWatched(streamId: Int, timestamp: Long)

    @Query("UPDATE channels SET lcn = :lcn WHERE streamId = :streamId")
    suspend fun updateLcn(streamId: Int, lcn: Int)

    @Query("UPDATE channels SET userCodecOverride = :codec, userQualityOverride = :quality WHERE streamId = :streamId")
    suspend fun updateUserOverrides(streamId: Int, codec: String?, quality: String?)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Query("SELECT DISTINCT groupTag FROM channels WHERE isHidden = 0 ORDER BY groupTag")
    fun observeAllGroupTags(): Flow<List<String>>

    @Query("SELECT DISTINCT countryCode FROM channels WHERE countryCode IS NOT NULL AND isHidden = 0 ORDER BY countryCode")
    fun observeAllCountryCodes(): Flow<List<String>>
}
