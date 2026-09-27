package com.nothing.one.data

import com.nothing.one.data.local.EntryDao
import com.nothing.one.data.local.EntryEntity
import com.nothing.one.data.model.EntryKind
import com.nothing.one.data.model.EntrySource
import com.nothing.one.data.model.Mood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Streak/trend results computed in Kotlin from DAO rows. */
data class MoodCounts(val counts: Map<Int, Int>)

@Singleton
class EntryRepository @Inject constructor(
    private val dao: EntryDao,
) {

    // ---- Notes -----------------------------------------------------------

    fun observeNotes(query: String): Flow<List<EntryEntity>> =
        if (query.isBlank()) dao.observeNotes() else dao.searchNotes(query.trim())

    fun observeArchivedNotes(): Flow<List<EntryEntity>> = dao.observeArchivedNotes()

    fun observeNoteCount(): Flow<Long> = dao.observeNoteCount()

    fun observeById(id: Long): Flow<EntryEntity?> = dao.observeById(id)

    suspend fun byId(id: Long): EntryEntity? = dao.byId(id)

    suspend fun upsertNote(
        id: Long?,
        title: String,
        body: String,
        tags: List<String>,
        source: EntrySource = EntrySource.TYPED,
        pinned: Boolean = false,
    ): Long {
        val now = System.currentTimeMillis()
        return if (id == null || id == 0L) {
            dao.insert(
                EntryEntity(
                    kind = EntryKind.NOTE,
                    title = title.trim(),
                    body = body,
                    tagsJson = Tags.encode(tags),
                    source = source,
                    pinned = pinned,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } else {
            val existing = dao.byId(id) ?: return -1
            dao.update(
                existing.copy(
                    title = title.trim(),
                    body = body,
                    tagsJson = Tags.encode(tags),
                    updatedAt = now,
                )
            )
            id
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    suspend fun setArchived(id: Long, archived: Boolean) =
        dao.setArchived(id, archived, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun setSummary(id: Long, summary: String?) {
        val existing = dao.byId(id) ?: return
        dao.update(existing.copy(summary = summary, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setTags(id: Long, tags: List<String>) {
        val existing = dao.byId(id) ?: return
        dao.update(existing.copy(tagsJson = Tags.encode(tags), updatedAt = System.currentTimeMillis()))
    }

    // ---- Journal -----------------------------------------------------------

    fun observeJournalByDay(dayIndex: Long): Flow<EntryEntity?> =
        dao.observeJournalByDay(dayIndex)

    fun observeRecentJournal(limit: Int = 60): Flow<List<EntryEntity>> =
        dao.observeRecentJournal(limit)

    /** Today's journal row, creating it if absent. The one-per-day invariant lives here. */
    suspend fun todayEntry(zone: ZoneId = ZoneId.systemDefault()): EntryEntity {
        val today = todayIndex(zone)
        return dao.journalByDay(today) ?: run {
            val entity = EntryEntity(kind = EntryKind.JOURNAL_ENTRY, dayIndex = today)
            val id = dao.insert(entity)
            entity.copy(id = id)
        }
    }

    suspend fun saveJournalText(entryId: Long, body: String) {
        val existing = dao.byId(entryId) ?: return
        dao.update(existing.copy(body = body, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveJournalMood(entryId: Long, mood: Mood?) {
        val existing = dao.byId(entryId) ?: return
        dao.update(existing.copy(mood = mood?.value, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setJournalSummary(entryId: Long, summary: String?) {
        val existing = dao.byId(entryId) ?: return
        dao.update(existing.copy(summary = summary, updatedAt = System.currentTimeMillis()))
    }

    suspend fun appendToToday(
        text: String,
        source: EntrySource = EntrySource.DICTATED,
        zone: ZoneId = ZoneId.systemDefault(),
    ): EntryEntity {
        val entry = todayEntry(zone)
        val stamped = (entry.body + "\n\n" + text).trim()
        dao.update(entry.copy(body = stamped, updatedAt = System.currentTimeMillis()))
        return entry.copy(body = stamped)
    }

    // ---- Insights -----------------------------------------------------------

    fun observeMoodCounts(): Flow<Map<Int, Int>> =
        dao.observeMoodCounts().map { rows -> rows.associate { it.mood to it.count } }

    fun observeMoodSeries(): Flow<List<Pair<Long, Int>>> =
        dao.observeMoodSeries().map { rows -> rows.map { it.dayIndex to it.mood } }

    fun observeJournalDays(): Flow<List<Long>> = dao.observeJournalDays()

    fun observeAllNoteTags(): Flow<List<String>> =
        dao.observeAllNoteTags().map { list -> list.flatMap { Tags.decode(it) } }

    companion object {
        /** Days since 1970-01-01 in [zone] — the journal's stable day key. */
        fun todayIndex(zone: ZoneId = ZoneId.systemDefault()): Long =
            LocalDate.now(zone).toEpochDay()
    }
}
