package com.nothing.one.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One chat turn with the assistant. History persists so conversations
 * survive process death and the assistant's context can be rebuilt.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "user" or "assistant". */
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** JSON action extracted from an assistant reply, if any. */
    val actionJson: String? = null,
    /** Whether the action in [actionJson] was confirmed and executed. */
    val actionDone: Boolean = false,
)

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC, id DESC LIMIT :n")
    suspend fun recent(n: Int): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET actionDone = 1 WHERE id = :id")
    suspend fun markActionDone(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
