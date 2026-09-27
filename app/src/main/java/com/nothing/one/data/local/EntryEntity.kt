package com.nothing.one.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nothing.one.data.model.EntryKind
import com.nothing.one.data.model.EntrySource

/**
 * A single row holds both quick notes and daily journal entries — they share
 * almost everything (text, source, timestamps), and kind + dayIndex separate
 * the two flows without a join table.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EntryKind,
    val title: String = "",
    val body: String = "",
    /** Comma-free tags stored as a JSON array string. */
    val tagsJson: String = "[]",
    /** Only meaningful for [EntryKind.JOURNAL_ENTRY]: days since 1970-01-01. */
    val dayIndex: Long? = null,
    /** 1..5 mood scale, journal entries only. */
    val mood: Int? = null,
    /** A local-AI one-paragraph digest, when the user asked for one. */
    val summary: String? = null,
    val source: EntrySource = EntrySource.TYPED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
)
