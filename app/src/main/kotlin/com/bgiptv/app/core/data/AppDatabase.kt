package com.bgiptv.app.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bgiptv.app.core.data.dao.ChannelDao
import com.bgiptv.app.core.data.dao.ProgramDao
import com.bgiptv.app.core.data.dao.SportEventDao
import com.bgiptv.app.core.data.dao.WatchHistoryDao
import com.bgiptv.app.core.data.entity.ChannelEntity
import com.bgiptv.app.core.data.entity.ProgramEntity
import com.bgiptv.app.core.data.entity.SportEventEntity
import com.bgiptv.app.core.data.entity.WatchHistoryEntity

@Database(
    entities = [
        ChannelEntity::class,
        ProgramEntity::class,
        SportEventEntity::class,
        WatchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun programDao(): ProgramDao
    abstract fun sportEventDao(): SportEventDao
    abstract fun watchHistoryDao(): WatchHistoryDao
}
