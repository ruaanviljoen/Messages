@file:Suppress("MagicNumber")
package org.fossify.messages.helpers

import android.app.Activity
import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.extensions.getMMSSender
import org.fossify.messages.extensions.updateLastConversationMessage
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.FilteredMessage

// A rule can delete the messages it files once they are older than its "Delete after" setting.

/** The choices for "Delete after", in days, each with its label; 0 keeps messages. */
val DELETE_AFTER_CHOICES = listOf(
    0 to R.string.delete_after_never,
    2 to R.string.delete_after_2_days,
    7 to R.string.delete_after_1_week,
    14 to R.string.delete_after_2_weeks,
    30 to R.string.delete_after_1_month
)

/** Asks how long a rule keeps the messages it files, in days. */
fun Activity.chooseDeleteAfter(days: Int, callback: (Int) -> Unit) {
    val items = DELETE_AFTER_CHOICES.mapTo(ArrayList()) { (choice, label) -> RadioItem(choice, getString(label)) }
    RadioGroupDialog(this, items, days, R.string.delete_after) { callback(it as Int) }
}

private const val SECONDS_PER_DAY = 86_400L
private const val CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L

private fun nowSeconds() = System.currentTimeMillis() / MILLIS_PER_SECOND

// When a message from [date], in seconds, becomes older than this rule keeps the messages it files, or null if never.
private fun FilterRule.expiresAt(date: Int): Long? =
    if (deleteAfterDays > 0 && !archives()) date + deleteAfterDays * SECONDS_PER_DAY else null

/** Whether a message from [date], in seconds, is older than this rule keeps the messages it files. */
fun FilterRule.expired(date: Int, now: Long = nowSeconds()): Boolean = expiresAt(date)?.let { it < now } == true

/**
 * Whether a filed message from [date] may be deleted without asking: it is too old now, and it became too old after the
 * rule's setting was chosen. Messages that were already too old then are only deleted by a run the user confirms.
 */
fun FilterRule.expiredSinceChosen(date: Int, now: Long = nowSeconds()): Boolean =
    expiresAt(date)?.let { it in deleteAfterSince until now } == true

private val autoDeleteLock = Any()

/**
 * Deletes the filed messages that have become older than the rule that files them allows, as in
 * [FilterRule.expiredSinceChosen]. Messages restored to the inbox, and messages no rule files any more, are kept.
 * Runs at most every few hours, when the app starts or a message arrives. Run it off the main thread.
 */
fun Context.deleteExpiredMessagesIfDue() {
    val due = synchronized(autoDeleteLock) {
        val now = System.currentTimeMillis()
        (now - config.lastAutoDeleteCheck >= CHECK_INTERVAL_MILLIS).also { if (it) config.lastAutoDeleteCheck = now }
    }
    if (due) {
        deleteExpiredMessages()
    }
}

/** Does the work of [deleteExpiredMessagesIfDue] now. Run it off the main thread. */
fun Context.deleteExpiredMessages() {
    val filter = MessageFilter(this)
    val folders = filter.rules.filter { it.deleteAfterDays > 0 }.mapTo(HashSet()) { it.folderId }
    if (folders.isEmpty()) {
        return
    }

    // only folders that a rule deletes from are read, which keeps this quick
    val expired = filteredDB.getFiled().filter { it.folderId in folders }.filter { entry ->
        val (address, body) = readFiledMessage(entry) ?: return@filter false
        val result = filter.filter(address, body) ?: return@filter false
        result.folderId == entry.folderId && result.rule.expiredSinceChosen(entry.date)
    }
    if (expired.isNotEmpty()) {
        expired.forEach { deleteMessage(it.id, it.isMMS) }
        updateLastConversationMessage(expired.map { it.threadId }.distinct())
        filterRulesDB.deleteUnusedFolders()
        refreshMessages()
        refreshConversations()
    }
}

// The sender and text of a filed message, or null if it is gone or its id now belongs to another message.
private fun Context.readFiledMessage(entry: FilteredMessage): Pair<String, String>? {
    var found: Pair<String, String>? = null
    if (entry.isMMS) {
        queryCursor(Mms.CONTENT_URI, arrayOf(Mms.DATE), "${Mms._ID} = ${entry.id}") {
            if (it.getIntValue(Mms.DATE) == entry.date) {
                found = getMMSSender(entry.id) to getMmsTexts(entry.id)[entry.id].orEmpty()
            }
        }
    } else {
        queryCursor(Sms.CONTENT_URI, arrayOf(Sms.DATE, Sms.ADDRESS, Sms.BODY), "${Sms._ID} = ${entry.id}") {
            if ((it.getLongValue(Sms.DATE) / MILLIS_PER_SECOND).toInt() == entry.date) {
                found = it.getStringValue(Sms.ADDRESS).orEmpty() to it.getStringValue(Sms.BODY).orEmpty()
            }
        }
    }
    return found
}
