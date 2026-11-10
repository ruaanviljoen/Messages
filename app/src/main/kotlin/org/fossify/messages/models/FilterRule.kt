package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.fossify.messages.helpers.SPAM_ACTION_DELETE
import org.fossify.messages.helpers.SPAM_ACTION_MOVE

/**
 * A filter rule: messages from [sender] that contain any of the [keywords] (one per line) are moved to the folder
 * [folderId], or deleted when [action] says so, which only the Spam rule allows. Either part may be empty, but not
 * both. Messages from saved contacts are left alone unless [applyToContacts], which is on by default; the Spam rule
 * starts with it off, so that saved contacts are not treated as spam.
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

    /** Whether matching messages are deleted instead of moved. Only the Spam rule may delete, for now. */
    fun deletes() = isSpam && action == SPAM_ACTION_DELETE

    /** A short description such as: FNB, "purchase", "payment". */
    fun describe() = (listOf(sender.trim()).filter { it.isNotEmpty() } + keywordList().map { "\"$it\"" })
        .joinToString(", ")
}
