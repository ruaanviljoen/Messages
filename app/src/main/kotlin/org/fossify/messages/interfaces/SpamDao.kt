package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.SpamMessage

@Dao
interface SpamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(spamMessage: SpamMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(spamMessages: List<SpamMessage>)

    @Query("SELECT * FROM spam_messages")
    fun getAll(): List<SpamMessage>

    @Query("SELECT * FROM spam_messages WHERE restored = 0")
    fun getSpam(): List<SpamMessage>

    @Query("SELECT * FROM spam_messages WHERE restored = 0 AND thread_id IN (:threadIds)")
    fun getThreadsSpam(threadIds: List<Long>): List<SpamMessage>

    @Query("DELETE FROM spam_messages WHERE id = :id AND is_mms = :isMMS")
    fun delete(id: Long, isMMS: Boolean)
}
