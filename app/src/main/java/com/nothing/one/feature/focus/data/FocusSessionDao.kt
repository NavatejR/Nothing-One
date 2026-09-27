package com.nothing.one.feature.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FocusSessionEntity>>

    /** Sessions that started within the last [sinceEpochMs] window. */
    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :sinceEpochMs ORDER BY startedAt DESC")
    fun observeSince(sinceEpochMs: Long): Flow<List<FocusSessionEntity>>

    @Query(
        """
        SELECT DISTINCT startedAt / 86400000 FROM focus_sessions
        WHERE kind = 'FOCUS' AND completed = 1
        ORDER BY startedAt / 86400000 ASC
        """
    )
    fun observeFocusDays(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun count(): Int
}
