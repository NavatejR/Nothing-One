package com.nothing.one.feature.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One logged focus/pomodoro session. Sessions persist so the assistant's
 * memory layer can answer "how much did I focus this week" and the Home
 * dashboard can show streaks — all computed locally.
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch ms when the session started. */
    val startedAt: Long,
    /** Epoch ms when the session ended (completed, skipped or stopped). */
    val endedAt: Long,
    /** Wall-time length of the session in ms (endedAt - startedAt). */
    val durationMs: Long,
    /** One of [FocusSessionKind]. */
    val kind: String,
    /** True when the session ran to its planned end (not skipped). */
    val completed: Boolean,
)

/** Session kinds. */
enum class FocusSessionKind { FOCUS, SHORT_BREAK, LONG_BREAK, TIMER }
