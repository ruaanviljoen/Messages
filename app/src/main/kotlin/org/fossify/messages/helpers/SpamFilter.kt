package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.databases.FilterDatabase
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.moveMessageToRecycleBin
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

/** How a sender is compared: a number by its last digits, a sender name such as "FNB" by its text, ignoring case. */
fun String.toSenderKey() = toComparableNumber().ifEmpty { trim().lowercase() }

/** The spam filter's current settings, read once. Reuse one instance when checking many messages. */
class SpamFilter(private val context: Context) {
    private val keywords = context.config.blockedKeywords
    private val exemptNumbers = context.config.spamExemptNumbers.map { it.toSenderKey() }.toSet()
    private val filterSavedContacts = context.config.filterSavedContacts
    private val savedContacts = HashMap<String, Boolean>()
    val deletes = context.config.spamAction == SPAM_ACTION_DELETE

    /** The keywords that make a message from [address] spam; empty when it isn't spam. */
    fun findKeywords(address: String, body: String): List<String> {
        val found = findSpamKeywords(body, keywords)
        return if (found.isEmpty() || isExempt(address)) emptyList() else found
    }

    fun isSpam(address: String, body: String) = findKeywords(address, body).isNotEmpty()

    private fun isExempt(address: String): Boolean {
        return address.toSenderKey() in exemptNumbers || (!filterSavedContacts && isSavedContact(address))
    }

    private fun isSavedContact(address: String) = savedContacts.getOrPut(address) {
        SimpleContactsHelper(context).existsSync(address) == ContactLookupResult.Found
    }
}

fun Message.toFilteredMessage(restored: Boolean = false, folderId: Long = SPAM_FOLDER_ID) =
    FilteredMessage(id, isMMS, threadId, date, restored, folderId)

/** Deletes a message, to the recycle bin when it is enabled, and forgets its folder entry. */
fun Context.deleteFilteredMessage(message: Message) {
    if (config.useRecycleBin) {
        messagesDB.insertOrUpdate(message)
        moveMessageToRecycleBin(message.id)
    } else {
        deleteMessage(message.id, message.isMMS)
    }
    filteredDB.delete(message.id, message.isMMS)
}

/** Returns a message to the inbox; later runs of the filter leave it there. */
fun Context.restoreToInbox(message: Message) = filteredDB.insert(message.toFilteredMessage(restored = true))

/** A conversation's messages in folders, by message id and type. Run it off the main thread. */
fun Context.getFilteredMessages(threadId: Long) =
    filteredDB.getThreadsFiled(listOf(threadId)).associateBy { it.id to it.isMMS }
