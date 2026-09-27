package com.nothing.one.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nothing.one.feature.focus.data.FocusSessionEntity

@Database(
    entities = [EntryEntity::class, FocusSessionEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    abstract fun focusSessionDao(): com.nothing.one.feature.focus.data.FocusSessionDao

    abstract fun chatMessageDao(): ChatMessageDao
}
