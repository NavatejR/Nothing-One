package com.nothing.one.data.model

/**
 * One entry. A [Note] is a quick capture (any time, optional title + tags);
 * a [JournalEntry] is the day page (one per calendar day, with a mood).
 */
enum class EntryKind { NOTE, JOURNAL_ENTRY }

/** Where an entry came from: typed, dictated, or appended by the assistant. */
enum class EntrySource { TYPED, DICTATED, ASSISTANT }
