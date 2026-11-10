package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.fossify.messages.helpers.SPAM_ACTION_ARCHIVE
import org.fossify.messages.helpers.SPAM_ACTION_MOVE

/**
 * A filter rule: messages from [sender] that contain any of the [keywords] (one per line) are moved to the folder
 * [folderId]. The Spam rule can archive their conversations instead, when [action] says so. Either part may be empty,
 * but not both. Messages from saved contacts are left alone unless [applyToContacts], which is on by default; the
 * Spam rule starts with it off, so that saved contacts are not treated as spam.
 *
 * The Spam rule ([isSpam]) is pinned: it runs first, its folder is Spam, and it cannot be moved or deleted. The other
 * rules run after it in [position] order, and the first rule that matches wins.
 */
@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "keywords") val keywords: String,
    @ColumnInfo(name = "notify") val notify: Boolean = false,
    @ColumnInfo(name = "action", defaultValue = "0") val action: Int = SPAM_ACTION_MOVE,
    @ColumnInfo(name = "is_spam", defaultValue = "0") val isSpam: Boolean = false,
    @ColumnInfo(name = "apply_to_contacts", defaultValue = "1") val applyToContacts: Boolean = true
) {
    fun keywordList() = keywords.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Whether the conversations of matching messages are archived instead. Only the Spam rule may do this, for now. */
    fun archives() = isSpam && action == SPAM_ACTION_ARCHIVE

    /** A short description such as: FNB, "purchase", "payment". */
    fun describe() = (listOf(sender.trim()).filter { it.isNotEmpty() } + keywordList().map { "\"$it\"" })
        .joinToString(", ")
}
