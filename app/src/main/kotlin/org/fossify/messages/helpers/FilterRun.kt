package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import androidx.core.net.toUri
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getMMSSender
import org.fossify.messages.models.FilteredMessage
import org.fossify.messages.models.SPAM_FOLDER_ID

internal const val MILLIS_PER_SECOND = 1000

/** How many changing messages a filter takes, and how many of them match each of its keywords. */
class FilterMatches {
    var total = 0
        private set
    val keywordCounts = HashMap<String, Int>()

    fun add(keywords: List<String>) {
        total++
        keywords.forEach { keywordCounts.merge(it, 1, Int::plus) }
    }
}

/**
 * What running the filters over the received messages on the phone would change: messages moving to a folder (Spam or
 * a rule's folder) or back to the inbox, messages whose conversations the Spam rule archives, and conversations it
 * archived earlier that it no longer matches ([unmatchedArchived], by thread id; [toUnarchive] are still archived). Of the messages that change, [ruleMatches]
 * counts what each rule takes, by rule id; the Spam rule is one of them.
 */
data class FilterRun(
    val toFolders: List<FilteredMessage>,
    val toInbox: List<FilteredMessage>,
    val toArchive: List<FilteredMessage>,
    val unmatchedArchived: Set<Long>,
    val toUnarchive: Set<Long>,
    val stale: List<FilteredMessage>,
    val ruleMatches: Map<Long, FilterMatches>
) {
    val hasChanges get() = toFolders.isNotEmpty() || toInbox.isNotEmpty() || toArchive.isNotEmpty() ||
        toUnarchive.isNotEmpty()
}

/**
 * Works out what the filters would change, without changing anything. Messages the user restored to the inbox are
 * left alone. Entries whose message is gone, or whose id now belongs to another message, are [FilterRun.stale]. Slow,
 * so run it off the main thread.
 */
fun Context.planFilterRun(): FilterRun {
    val filter = MessageFilter(this)
    val entries = filteredDB.getAll().associateByTo(HashMap()) { it.id to it.isMMS }
    val toFolders = ArrayList<FilteredMessage>()
    val toInbox = ArrayList<FilteredMessage>()
    val toArchive = ArrayList<FilteredMessage>()
    val archived = conversationsDB.getAllArchived().mapTo(HashSet()) { it.threadId }
    val archivedBySpam = getSpamArchivedThreads()
    val spamThreads = HashSet<Long>()
    val stale = ArrayList<FilteredMessage>()
    val ruleMatches = HashMap<Long, FilterMatches>()

    forEachReceivedMessage { message, address, body ->
        val stored = entries.remove(message.id to message.isMMS)
        val entry = stored?.takeIf { it.date == message.date }
        if (stored != null && entry == null) {
            stale += stored
        }

        if (entry?.restored == true) {
            return@forEachReceivedMessage
        }

        val result = filter.filter(address, body)
        when {
            result == null -> if (entry != null) toInbox += entry
            // A message to archive leaves its folder. Its conversation is archived once, so one that the user
            // unarchived, or archived themselves, is left alone.
            result.archives -> {
                spamThreads += message.threadId
                if (entry != null || (message.threadId !in archived && message.threadId !in archivedBySpam)) {
                    toArchive += message
                } else {
                    return@forEachReceivedMessage
                }
            }

            entry?.folderId != result.folderId -> toFolders += message.copy(folderId = result.folderId)
            else -> return@forEachReceivedMessage // already in its folder
        }

        // counted only when the message changes, so messages already in their folder are not reported again
        result?.let { ruleMatches.getOrPut(it.rule.id) { FilterMatches() }.add(it.keywords) }
    }

    // An entry not seen above is stale only if its message is really gone, not merely missed by a failed read.
    stale += entries.values.filterNot { messageExists(it) }
    // conversations the Spam rule archived that it no longer matches; the user may have unarchived some already
    val unmatchedArchived = archivedBySpam - spamThreads
    val toUnarchive = unmatchedArchived.filterTo(HashSet()) { it in archived }
    return FilterRun(toFolders, toInbox, toArchive, unmatchedArchived, toUnarchive, stale, ruleMatches)
}

/** Applies a run worked out by [planFilterRun]. Run it off the main thread. */
fun Context.applyFilterRun(run: FilterRun) {
    // Moving or archiving existing messages leaves their read state as it is. Where a conversation cannot be
    // archived, its messages go to the Spam folder instead.
    val notArchived = run.toArchive.map { it.threadId }.distinct().filterNot { archiveSpamConversation(it) }.toSet()
    val (toSpam, archivedMessages) = run.toArchive.partition { it.threadId in notArchived }
    (run.stale + run.toInbox + archivedMessages).forEach { filteredDB.delete(it.id, it.isMMS) }
    filteredDB.insertAll(run.toFolders + toSpam.map { it.copy(folderId = SPAM_FOLDER_ID, restored = false) })
    unarchiveSpamConversations(run.toUnarchive, forget = run.unmatchedArchived)
    refreshMessages()
    refreshConversations()
}

// A failed query counts as "exists", so that a read error never removes an entry.
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun Context.messageExists(entry: FilteredMessage): Boolean {
    val uri = if (entry.isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    return try {
        contentResolver.query(uri, arrayOf(Sms._ID), "${Sms._ID} = ?", arrayOf(entry.id.toString()), null)
            ?.use { it.count > 0 } ?: true
    } catch (e: Exception) {
        true
    }
}

// Reading the received messages follows SpamScanner in Messages+ (https://github.com/arikw/messages).
private fun Context.forEachReceivedMessage(
    callback: (message: FilteredMessage, address: String, body: String) -> Unit
) {
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE),
        selection = "${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}",
        showErrors = true
    ) { cursor ->
        val message = FilteredMessage(
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
        val message = FilteredMessage(
            id = id,
            isMMS = true,
            threadId = cursor.getLongValue(Mms.THREAD_ID),
            date = cursor.getIntValue(Mms.DATE)
        )
        callback(message, getMMSSender(id), mmsTexts[id].orEmpty())
    }
}

// The text parts of all MMS, or of one, by message id. Also used by search and the inbox.
internal fun Context.getMmsTexts(messageId: Long? = null): Map<Long, String> {
    val texts = HashMap<Long, String>()
    queryCursor(
        uri = "content://mms/part".toUri(),
        projection = arrayOf(Mms.Part.MSG_ID, Mms.Part.TEXT),
        selection = "${Mms.Part.CONTENT_TYPE} = 'text/plain'" +
            if (messageId == null) "" else " AND ${Mms.Part.MSG_ID} = $messageId",
        showErrors = true
    ) { cursor ->
        val text = cursor.getStringValue(Mms.Part.TEXT) ?: return@queryCursor
        texts.merge(cursor.getLongValue(Mms.Part.MSG_ID), text) { old, new -> "$old $new" }
    }
    return texts
}
