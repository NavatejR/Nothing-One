package com.nothing.one.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nothing.one.data.model.EntryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    // ---- Notes -----------------------------------------------------------

    @Query(
        """
        SELECT * FROM entries
        WHERE kind = 'NOTE' AND archived = 0
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeNotes(): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT * FROM entries
        WHERE kind = 'NOTE' AND archived = 0
          AND (title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' OR tagsJson LIKE '%' || :q || '%')
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun searchNotes(q: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE kind = 'NOTE' AND archived = 1 ORDER BY updatedAt DESC")
    fun observeArchivedNotes(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun byId(id: Long): EntryEntity?

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<EntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("UPDATE entries SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long)

    @Query("UPDATE entries SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- Journal ----------------------------------------------------------

    @Query("SELECT * FROM entries WHERE kind = 'JOURNAL_ENTRY' AND dayIndex = :dayIndex LIMIT 1")
    suspend fun journalByDay(dayIndex: Long): EntryEntity?

    @Query("SELECT * FROM entries WHERE kind = 'JOURNAL_ENTRY' AND dayIndex = :dayIndex LIMIT 1")
    fun observeJournalByDay(dayIndex: Long): Flow<EntryEntity?>

    @Query(
        """
        SELECT * FROM entries
        WHERE kind = 'JOURNAL_ENTRY' AND dayIndex IS NOT NULL
        ORDER BY dayIndex DESC
        LIMIT :limit
        """
    )
    fun observeRecentJournal(limit: Int): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT mood, COUNT(*) as count FROM entries
        WHERE kind = 'JOURNAL_ENTRY' AND mood IS NOT NULL AND dayIndex IS NOT NULL
        GROUP BY mood
        """
    )
    fun observeMoodCounts(): Flow<List<MoodCountRow>>

    @Query(
        """
        SELECT mood, dayIndex FROM entries
        WHERE kind = 'JOURNAL_ENTRY' AND mood IS NOT NULL AND dayIndex IS NOT NULL
        ORDER BY dayIndex ASC
        """
    )
    fun observeMoodSeries(): Flow<List<MoodPointRow>>

    /** All distinct journal day indices, ascending — streaks are computed from this. */
    @Query(
        """
        SELECT DISTINCT dayIndex FROM entries
        WHERE kind = 'JOURNAL_ENTRY' AND dayIndex IS NOT NULL
        ORDER BY dayIndex ASC
        """
    )
    fun observeJournalDays(): Flow<List<Long>>

    // ---- Insights ----------------------------------------------------------

    @Query("SELECT COUNT(*) FROM entries WHERE kind = 'NOTE' AND archived = 0")
    fun observeNoteCount(): Flow<Long>

    @Query("SELECT tagsJson FROM entries WHERE kind = 'NOTE' AND archived = 0")
    fun observeAllNoteTags(): Flow<List<String>>
}

/** Projection rows for aggregate queries. */
data class MoodCountRow(val mood: Int, val count: Int)

data class MoodPointRow(val mood: Int, val dayIndex: Long)
