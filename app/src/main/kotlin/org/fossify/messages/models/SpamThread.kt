package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How the inbox last showed a conversation whose newest message was spam: hidden ([snippet] null) or with the newest
 * message that isn't spam. It lets the inbox appear at once on start; [conversationDate] tells whether it still holds.
 */
@Entity(tableName = "spam_threads")
data class SpamThread(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "conversation_date") val conversationDate: Int,
    @ColumnInfo(name = "snippet") val snippet: String?,
    @ColumnInfo(name = "snippet_date") val snippetDate: Int
)
