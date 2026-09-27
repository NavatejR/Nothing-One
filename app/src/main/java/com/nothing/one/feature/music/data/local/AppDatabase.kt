package com.nothing.one.feature.music.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nothing.one.feature.music.data.local.dao.FavoriteDao
import com.nothing.one.feature.music.data.local.dao.PlaylistDao
import com.nothing.one.feature.music.data.local.dao.ScannedFolderDao
import com.nothing.one.feature.music.data.local.dao.SearchHistoryDao
import com.nothing.one.feature.music.data.local.entity.FavoriteEntity
import com.nothing.one.feature.music.data.local.entity.PlaylistEntity
import com.nothing.one.feature.music.data.local.entity.PlaylistTrackEntity
import com.nothing.one.feature.music.data.local.entity.ScannedFolderEntity
import com.nothing.one.feature.music.data.local.entity.SearchHistoryEntity

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        ScannedFolderEntity::class,
        FavoriteEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun scannedFolderDao(): ScannedFolderDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nothing_music.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}