package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.databases.SpamDatabase
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.extensions.markMessageRead
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.moveMessageToRecycleBin
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message
import org.fossify.messages.models.SpamMessage

private const val COMPARABLE_NUMBER_DIGITS = 9

val Context.spamDB get() = SpamDatabase.getInstance(this).SpamDao()

/** The keywords that occur anywhere in [body], ignoring case. */
fun findSpamKeywords(body: String, keywords: Collection<String>): List<String> {
    return keywords.filter { it.isNotEmpty() && body.contains(it, ignoreCase = true) }
}

/** The last nine digits of a phone number, so that local (082...) and international (+2782...) forms compare equal. */
fun String.toComparableNumber() = filter(Char::isDigit).takeLast(COMPARABLE_NUMBER_DIGITS)

/** The spam filter's current settings, read once. Reuse one instance when checking many messages. */
class SpamFilter(private val context: Context) {
    private val keywords = context.config.blockedKeywords
    // numbers compare by their last digits, sender names such as "FNB" by their text
    private val exemptNumbers = context.config.spamExemptNumbers.map { it.toExemptKey() }.toSet()
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
        return address.toExemptKey() in exemptNumbers || (!filterSavedContacts && isSavedContact(address))
    }

    private fun String.toExemptKey() = toComparableNumber().ifEmpty { trim().lowercase() }

    private fun isSavedContact(address: String) = savedContacts.getOrPut(address) {
        SimpleContactsHelper(context).existsSync(address) == ContactLookupResult.Found
    }
}

fun Message.toSpamMessage(restored: Boolean = false) = SpamMessage(id, isMMS, threadId, date, restored)

/** Moves a received message that matched the filter to Spam, or deletes it, without notifying. */
fun Context.handleReceivedSpam(message: Message) {
    if (config.spamAction == SPAM_ACTION_DELETE) {
        deleteFilteredMessage(message)
    } else {
        markMessageRead(message.id, message.isMMS)
        spamDB.insert(message.toSpamMessage())
    }
    refreshMessages()
    refreshConversations()
}

/** Deletes a message, to the recycle bin when it is enabled, and forgets its Spam entry. */
fun Context.deleteFilteredMessage(message: Message) {
    if (config.useRecycleBin) {
        messagesDB.insertOrUpdate(message)
        moveMessageToRecycleBin(message.id)
    } else {
        deleteMessage(message.id, message.isMMS)
    }
    spamDB.delete(message.id, message.isMMS)
}

/** Returns a message to the inbox; later runs of the filter leave it there. */
fun Context.restoreFromSpam(message: Message) = spamDB.insert(message.toSpamMessage(restored = true))

/** The dates of a conversation's messages in Spam, by message id and type. Run it off the main thread. */
fun Context.getSpamDates(threadId: Long) =
    spamDB.getThreadsSpam(listOf(threadId)).associate { (it.id to it.isMMS) to it.date }

/**
 * [conversations] as the inbox shows them: without the ones whose messages are all in Spam, and with the newest message
 * that isn't spam as the snippet and date. A conversation that can't be read is shown unchanged. Run it off the main
 * thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun Context.withoutSpamOnly(conversations: List<Conversation>): ArrayList<Conversation> {
    val spam = spamDB.getSpam().groupBy { it.threadId }
    return conversations.mapNotNullTo(ArrayList()) { conversation ->
        val entries = spam[conversation.threadId]
        if (entries == null || entries.maxOf { it.date } < conversation.date) {
            return@mapNotNullTo conversation
        }

        try {
            val dates = entries.associate { (it.id to it.isMMS) to it.date }
            newestNonSpam(conversation.threadId, dates)?.let { (body, date) ->
                conversation.copy(snippet = body, date = date)
            }
        } catch (e: Exception) {
            conversation
        }
    }
}

/**
 * The body and date of the newest message in a conversation that isn't in Spam, or null if there is none. Every newer
 * message is in Spam, so it is among the newest [spamDates] size + 1 SMS or MMS. Throws if the store can't be read.
 */
private fun Context.newestNonSpam(threadId: Long, spamDates: Map<Pair<Long, Boolean>, Int>): Pair<String, Int>? {
    val selection = "thread_id = ?"
    val args = arrayOf(threadId.toString())
    val order = "date DESC LIMIT ${spamDates.size + 1}"
    val sms = contentResolver.query(Sms.CONTENT_URI, arrayOf(Sms._ID, Sms.DATE, Sms.BODY), selection, args, order)!!
        .use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .map { Triple(it.getLong(0), (it.getLong(1) / MILLIS_PER_SECOND).toInt(), it.getString(2).orEmpty()) }
                .firstOrNull { (id, date) -> spamDates[id to false] != date }
        }
    val mms = contentResolver.query(Mms.CONTENT_URI, arrayOf(Mms._ID, Mms.DATE), selection, args, order)!!
        .use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .map { it.getLong(0) to it.getInt(1) }
                .firstOrNull { (id, date) -> spamDates[id to true] != date }
        }

    return when {
        mms != null && (sms == null || mms.second > sms.second) ->
            getMmsTexts(mms.first)[mms.first].orEmpty() to mms.second
        sms != null -> sms.third to sms.second
        else -> null
    }
}
