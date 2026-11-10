package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.messages.databases.FilterDatabase
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.FilteredMessage
import org.fossify.messages.models.SpamThread

// How conversations with messages in folders (Spam or a rule's folder) appear in the inbox and in the folders.

val Context.spamThreadsDB get() = FilterDatabase.getInstance(this).SpamThreadsDao()

/** Returns a conversation's messages in a folder to the inbox. Run it off the main thread. */
fun Context.restoreConversationFromFolder(threadId: Long, folderId: Long) = filteredDB.insertAll(
    filteredDB.getThreadsFiled(listOf(threadId)).filter { it.folderId == folderId }.map { it.copy(restored = true) }
)

/** Deletes a conversation's messages in a folder and leaves its other messages. Run it off the main thread. */
fun Context.deleteConversationFromFolder(threadId: Long, folderId: Long) {
    val entries = getFilteredMessages(threadId)
    getMessages(threadId, includeScheduledMessages = false, limit = Int.MAX_VALUE)
        .filter { message ->
            val entry = entries[message.id to message.isMMS]
            entry != null && entry.date == message.date && entry.folderId == folderId
        }
        .forEach { deleteFilteredMessage(it) }
}

/**
 * The conversations that have messages in a folder, each with its newest message there as the snippet. Run it off the
 * main thread.
 */
fun Context.getFolderConversations(folderId: Long): ArrayList<Conversation> {
    return filteredDB.getFolder(folderId).groupBy { it.threadId }.mapNotNullTo(ArrayList()) { (threadId, entries) ->
        val newest = entries.sortedByDescending { it.date }.firstNotNullOfOrNull { entry ->
            getFilteredText(entry)?.let { it to entry.date }
        } ?: return@mapNotNullTo null
        val conversation = conversationsDB.getConversationWithThreadId(threadId)
            ?: getConversations(threadId).firstOrNull()
        conversation?.copy(snippet = newest.first, date = newest.second)
    }
}

// The text of a message in a folder, or null if it is gone or its id now belongs to another message.
private fun Context.getFilteredText(entry: FilteredMessage): String? {
    var text: String? = null
    if (entry.isMMS) {
        queryCursor(Mms.CONTENT_URI, arrayOf(Mms.DATE), "${Mms._ID} = ${entry.id}") {
            if (it.getInt(0) == entry.date) {
                text = getMmsTexts(entry.id)[entry.id].orEmpty()
            }
        }
    } else {
        queryCursor(Sms.CONTENT_URI, arrayOf(Sms.DATE, Sms.BODY), "${Sms._ID} = ${entry.id}") {
            if ((it.getLong(0) / MILLIS_PER_SECOND).toInt() == entry.date) {
                text = it.getStringValue(Sms.BODY).orEmpty()
            }
        }
    }
    return text
}

/**
 * [conversations] as the inbox shows them: without the ones whose messages are all in folders, and with the newest
 * message that isn't in a folder as the snippet and date. A conversation that can't be read is shown unchanged. What it
 * finds is stored for [withoutStoredSpamOnly]. Run it off the main thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun Context.withoutSpamOnly(conversations: List<Conversation>): ArrayList<Conversation> {
    val spam = filteredDB.getFiled().groupBy { it.threadId }
    val threads = ArrayList<SpamThread>()
    val shown = conversations.mapNotNullTo(ArrayList()) { conversation ->
        val entries = spam[conversation.threadId]
        if (entries == null || entries.maxOf { it.date } < conversation.date) {
            return@mapNotNullTo conversation
        }

        try {
            val dates = entries.associate { (it.id to it.isMMS) to it.date }
            val newest = newestNonSpam(conversation.threadId, dates)
            threads += SpamThread(
                threadId = conversation.threadId,
                conversationDate = conversation.date,
                snippet = newest?.first,
                snippetDate = newest?.second ?: 0
            )
            newest?.let { (body, date) -> conversation.copy(snippet = body, date = date) }
        } catch (e: Exception) {
            conversation
        }
    }
    spamThreadsDB.replaceAll(threads)
    return shown
}

/**
 * Like [withoutSpamOnly], but quick enough to show the inbox at once on start: it uses what [withoutSpamOnly] last
 * found, for conversations that haven't changed since. Run it off the main thread.
 */
fun Context.withoutStoredSpamOnly(conversations: List<Conversation>): ArrayList<Conversation> {
    val threads = spamThreadsDB.getAll().associateBy { it.threadId }
    return conversations.mapNotNullTo(ArrayList()) { conversation ->
        val thread = threads[conversation.threadId]?.takeIf { it.conversationDate == conversation.date }
        when {
            thread == null -> conversation
            thread.snippet == null -> null
            else -> conversation.copy(snippet = thread.snippet, date = thread.snippetDate)
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
