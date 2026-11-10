package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A filter rule: messages from [sender] that contain any of the [keywords] (one per line) go to the folder [folderId].
 * Either part may be empty, but not both. Rules run in [position] order, after Spam, and the first one that matches
 * wins.
 */
@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "keywords") val keywords: String,
    @ColumnInfo(name = "notify") val notify: Boolean = false
) {
    fun keywordList() = keywords.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** A short description such as: FNB, "purchase", "payment". */
    fun describe() = (listOf(sender.trim()).filter { it.isNotEmpty() } + keywordList().map { "\"$it\"" })
        .joinToString(", ")
}
