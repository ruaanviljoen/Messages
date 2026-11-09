package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import androidx.core.net.toUri
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.messages.extensions.getMMSSender
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.markMessageRead
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.models.SpamMessage

internal const val MILLIS_PER_SECOND = 1000

/** What running the spam filter over the received messages on the phone would change. */
data class SpamFilterRun(
    val toSpam: List<SpamMessage>,
    val toInbox: List<SpamMessage>,
    val toDelete: List<SpamMessage>,
    val stale: List<SpamMessage>,
    val keywordCounts: Map<String, Int>
) {
    val hasChanges get() = toSpam.isNotEmpty() || toInbox.isNotEmpty() || toDelete.isNotEmpty()
}

/**
 * Works out what the filter would change, without changing anything. Messages the user restored from Spam, and
 * messages already in the recycle bin, are left alone. Spam entries whose message is gone, or whose id now belongs to
 * another message, are [SpamFilterRun.stale]. Slow, so run it off the main thread.
 */
fun Context.planSpamFilterRun(): SpamFilterRun {
    val filter = SpamFilter(this)
    val entries = spamDB.getAll().associateByTo(HashMap()) { it.id to it.isMMS }
    val recycled = messagesDB.getAllRecycleBinMessages().map { it.id to it.isMMS }.toSet()
    val toSpam = ArrayList<SpamMessage>()
    val toInbox = ArrayList<SpamMessage>()
    val toDelete = ArrayList<SpamMessage>()
    val stale = ArrayList<SpamMessage>()
    val keywordCounts = HashMap<String, Int>()

    forEachReceivedMessage { message, address, body ->
        val stored = entries.remove(message.id to message.isMMS)
        val entry = stored?.takeIf { it.date == message.date }
        if (stored != null && entry == null) {
            stale += stored
        }

        if (entry?.restored == true || (message.id to message.isMMS) in recycled) {
            return@forEachReceivedMessage
        }

        val keywords = filter.findKeywords(address, body)
        keywords.forEach { keywordCounts.merge(it, 1, Int::plus) }
        when {
            keywords.isEmpty() -> if (entry != null) toInbox += entry
            filter.deletes -> toDelete += message
            entry == null -> toSpam += message
        }
    }

    // An entry not seen above is stale only if its message is really gone, not merely missed by a failed read.
    stale += entries.values.filterNot { messageExists(it) }
    return SpamFilterRun(toSpam, toInbox, toDelete, stale, keywordCounts)
}

/** Applies a run worked out by [planSpamFilterRun]. Run it off the main thread. */
fun Context.applySpamFilterRun(run: SpamFilterRun) {
    (run.stale + run.toInbox).forEach { spamDB.delete(it.id, it.isMMS) }
    spamDB.insertAll(run.toSpam)
    run.toSpam.forEach { markMessageRead(it.id, it.isMMS) }
    run.toDelete.groupBy { it.threadId }.forEach { (threadId, spam) ->
        val keys = spam.map { it.id to it.isMMS }.toSet()
        getMessages(threadId, includeScheduledMessages = false, limit = Int.MAX_VALUE)
            .filter { (it.id to it.isMMS) in keys }
            .forEach { deleteFilteredMessage(it) }
    }
    refreshMessages()
    refreshConversations()
}

// A failed query counts as "exists", so that a read error never removes an entry.
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun Context.messageExists(entry: SpamMessage): Boolean {
    val uri = if (entry.isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    return try {
        contentResolver.query(uri, arrayOf(Sms._ID), "${Sms._ID} = ?", arrayOf(entry.id.toString()), null)
            ?.use { it.count > 0 } ?: true
    } catch (e: Exception) {
        true
    }
}

// Reading the received messages follows SpamScanner in Messages+ (https://github.com/arikw/messages).
private fun Context.forEachReceivedMessage(callback: (message: SpamMessage, address: String, body: String) -> Unit) {
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE),
        selection = "${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}",
        showErrors = true
    ) { cursor ->
        val message = SpamMessage(
            id = cursor.getLongValue(Sms._ID),
            isMMS = false,
            threadId = cursor.getLongValue(Sms.THREAD_ID),
            date = (cursor.getLongValue(Sms.DATE) / MILLIS_PER_SECOND).toInt()
        )
        callback(message, cursor.getStringValue(Sms.ADDRESS).orEmpty(), cursor.getStringValue(Sms.BODY).orEmpty())
    }

    val mmsTexts = getMmsTexts()
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE),
        selection = "${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_INBOX}",
        showErrors = true
    ) { cursor ->
        val id = cursor.getLongValue(Mms._ID)
        val message = SpamMessage(id, isMMS = true, cursor.getLongValue(Mms.THREAD_ID), cursor.getIntValue(Mms.DATE))
        callback(message, getMMSSender(id), mmsTexts[id].orEmpty())
    }
}

// The text parts of all MMS, by message id. Also used by search.
internal fun Context.getMmsTexts(): Map<Long, String> {
    val texts = HashMap<Long, String>()
    queryCursor(
        uri = "content://mms/part".toUri(),
        projection = arrayOf(Mms.Part.MSG_ID, Mms.Part.TEXT),
        selection = "${Mms.Part.CONTENT_TYPE} = 'text/plain'",
        showErrors = true
    ) { cursor ->
        val text = cursor.getStringValue(Mms.Part.TEXT) ?: return@queryCursor
        texts.merge(cursor.getLongValue(Mms.Part.MSG_ID), text) { old, new -> "$old $new" }
    }
    return texts
}
