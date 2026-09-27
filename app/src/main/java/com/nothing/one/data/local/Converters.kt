package com.nothing.one.data.local

import androidx.room.TypeConverter
import com.nothing.one.data.model.EntryKind
import com.nothing.one.data.model.EntrySource

class Converters {
    @TypeConverter
    fun entryKindToString(kind: EntryKind): String = kind.name

    @TypeConverter
    fun stringToEntryKind(value: String): EntryKind =
        EntryKind.valueOf(value)

    @TypeConverter
    fun entrySourceToString(source: EntrySource): String = source.name

    @TypeConverter
    fun stringToEntrySource(value: String): EntrySource =
        EntrySource.valueOf(value)
}
