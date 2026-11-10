package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A received message the filters moved to a folder ([folderId], [SPAM_FOLDER_ID] for Spam), or one the user restored
 * to the inbox ([restored]), which later runs of the filters leave alone. The message itself stays in the phone's
 * message store. SMS and MMS ids come from different tables, hence `is_mms` in the key. [date] is in seconds and guards
 * against a reused id. The table keeps its original name from when only Spam existed.
 */
@Entity(tableName = "spam_messages", primaryKeys = ["id", "is_mms"], indices = [Index(value = ["thread_id"])])
data class FilteredMessage(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "is_mms") val isMMS: Boolean,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "date") val date: Int,
    @ColumnInfo(name = "restored") val restored: Boolean = false,
    @ColumnInfo(name = "folder_id", defaultValue = "0") val folderId: Long = SPAM_FOLDER_ID
)

const val SPAM_FOLDER_ID = 0L
