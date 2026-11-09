package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A received message the spam filter moved to Spam, or one the user restored from Spam ([restored]), which later runs
 * of the filter leave alone. The message itself stays in the phone's message store. SMS and MMS ids come from
 * different tables, hence `is_mms` in the key. [date] is in seconds and guards against a reused id.
 */
@Entity(tableName = "spam_messages", primaryKeys = ["id", "is_mms"], indices = [Index(value = ["thread_id"])])
data class SpamMessage(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "is_mms") val isMMS: Boolean,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "date") val date: Int,
    @ColumnInfo(name = "restored") val restored: Boolean = false
)
