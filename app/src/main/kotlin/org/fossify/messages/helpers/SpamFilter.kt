package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.databases.FilterDatabase
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.models.FilteredMessage
import org.fossify.messages.models.Message
import org.fossify.messages.models.SPAM_FOLDER_ID

private const val COMPARABLE_NUMBER_DIGITS = 9

val Context.filteredDB get() = FilterDatabase.getInstance(this).FilteredMessagesDao()

/** The keywords that occur anywhere in [body], ignoring case. */
fun findSpamKeywords(body: String, keywords: Collection<String>): List<String> {
    return keywords.filter { it.isNotEmpty() && body.contains(it, ignoreCase = true) }
}

/** The last nine digits of a phone number, so that local (082...) and international (+2782...) forms compare equal. */
fun String.toComparableNumber() = filter(Char::isDigit).takeLast(COMPARABLE_NUMBER_DIGITS)

private val PHONE_NUMBER = Regex("""^[+\d\s()-]*\d[+\d\s()-]*$""")

/**
 * How a sender is compared: a phone number by its last digits, a sender name such as "BANK" or "PROMO4U" by its text,
 * ignoring case.
 */
fun String.toSenderKey() = trim().let { if (PHONE_NUMBER.matches(it)) it.toComparableNumber() else it.lowercase() }

fun Message.toFilteredMessage(restored: Boolean = false, folderId: Long = SPAM_FOLDER_ID) =
    FilteredMessage(id, isMMS, threadId, date, restored, folderId)

/** Deletes a message permanently and forgets its folder entry. */
fun Context.deleteFilteredMessage(message: Message) {
    deleteMessage(message.id, message.isMMS)
    filteredDB.delete(message.id, message.isMMS)
}

/** Returns a message to the inbox; later runs of the filter leave it there. */
fun Context.restoreToInbox(message: Message) = filteredDB.insert(message.toFilteredMessage(restored = true))

/** A conversation's messages in folders, by message id and type. Run it off the main thread. */
fun Context.getFilteredMessages(threadId: Long) =
    filteredDB.getThreadsFiled(listOf(threadId)).associateBy { it.id to it.isMMS }
