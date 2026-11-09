package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Threads
import androidx.core.net.toUri
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.queryCursor
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

/** [conversations] without the ones whose messages are all in Spam. Run it off the main thread. */
fun Context.withoutSpamOnly(conversations: List<Conversation>): ArrayList<Conversation> {
    val spamCounts = spamDB.getSpam().groupingBy { it.threadId }.eachCount()
    if (spamCounts.isEmpty()) {
        return ArrayList(conversations)
    }

    val spamOnly = HashSet<Long>()
    queryCursor(
        uri = "${Threads.CONTENT_URI}?simple=true".toUri(),
        projection = arrayOf(Threads._ID, Threads.MESSAGE_COUNT),
        selection = "${Threads._ID} IN (${spamCounts.keys.joinToString(",")})"
    ) { cursor ->
        val threadId = cursor.getLongValue(Threads._ID)
        if (cursor.getIntValue(Threads.MESSAGE_COUNT) <= (spamCounts[threadId] ?: 0)) {
            spamOnly += threadId
        }
    }
    return conversations.filterNotTo(ArrayList()) { it.threadId in spamOnly }
}
