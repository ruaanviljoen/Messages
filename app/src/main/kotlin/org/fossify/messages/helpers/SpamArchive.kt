package org.fossify.messages.helpers

import android.content.Context
import android.database.sqlite.SQLiteException
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.updateConversationArchivedStatus

// Conversations the Spam rule archived are remembered, so that a later run of the filters leaves them alone when the
// user unarchived them, and returns them to the inbox when the rule no longer matches them.

private val spamArchiveLock = Any()

/** The conversations the Spam rule archived, by thread id. */
fun Context.getSpamArchivedThreads(): Set<Long> =
    config.spamArchivedThreads.mapNotNullTo(HashSet()) { it.toLongOrNull() }

private fun Context.updateSpamArchivedThreads(change: (MutableSet<Long>) -> Unit) = synchronized(spamArchiveLock) {
    val threads = getSpamArchivedThreads().toMutableSet().apply(change)
    config.spamArchivedThreads = threads.mapTo(HashSet()) { it.toString() }
}

/**
 * Archives a conversation for the Spam rule, with its newest message as the snippet. Returns false if the phone cannot
 * archive, in which case the caller files the spam to the Spam folder instead. Run it off the main thread.
 */
@Suppress("SwallowedException")
fun Context.archiveSpamConversation(threadId: Long): Boolean {
    try {
        updateConversationArchivedStatus(threadId, true)
    } catch (e: SQLiteException) {
        return false
    }
    // updateConversationArchivedStatus turns this off, instead of failing, when the phone has no archive
    if (!config.isArchiveAvailable) {
        return false
    }
    getConversations(threadId).firstOrNull()?.let { insertOrUpdateConversation(it) }
    updateSpamArchivedThreads { it += threadId }
    return true
}

/**
 * Returns conversations the Spam rule archived to the inbox, and forgets that it archived those in [forget]. Run it off
 * the main thread.
 */
@Suppress("SwallowedException")
fun Context.unarchiveSpamConversations(threadIds: Collection<Long>, forget: Collection<Long>) {
    threadIds.forEach {
        try {
            updateConversationArchivedStatus(it, false)
        } catch (e: SQLiteException) {
            return@forEach
        }
    }
    updateSpamArchivedThreads { it -= (forget + threadIds).toSet() }
}
