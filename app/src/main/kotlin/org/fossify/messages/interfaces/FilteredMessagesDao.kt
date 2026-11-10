package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.FilteredMessage

@Dao
interface FilteredMessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: FilteredMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(messages: List<FilteredMessage>)

    @Query("SELECT * FROM spam_messages")
    fun getAll(): List<FilteredMessage>

    @Query("SELECT * FROM spam_messages WHERE restored = 0")
    fun getFiled(): List<FilteredMessage>

    @Query("SELECT * FROM spam_messages WHERE restored = 0 AND folder_id = :folderId")
    fun getFolder(folderId: Long): List<FilteredMessage>

    @Query("SELECT * FROM spam_messages WHERE restored = 0 AND thread_id IN (:threadIds)")
    fun getThreadsFiled(threadIds: List<Long>): List<FilteredMessage>

    @Query("DELETE FROM spam_messages WHERE id = :id AND is_mms = :isMMS")
    fun delete(id: Long, isMMS: Boolean)

    @Query("DELETE FROM spam_messages WHERE thread_id = :threadId")
    fun deleteThread(threadId: Long)
}
