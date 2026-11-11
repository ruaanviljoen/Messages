package org.fossify.messages.helpers

import android.content.Context
import androidx.room.RoomDatabase
import org.fossify.messages.databases.FilterDatabase
import org.fossify.messages.databases.MessagesDatabase
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.models.Conversation

/** Runs a test's body off the main thread, where Room may be used, and rethrows what it throws. */
internal fun background(block: () -> Unit) {
    var error: Throwable? = null
    Thread { runCatching(block).onFailure { error = it } }.apply {
        start()
        join()
    }
    error?.let { throw it }
}

/** Closes and forgets the app's Room databases, so that each test starts empty. */
internal fun resetDatabases() {
    listOf(FilterDatabase::class.java, MessagesDatabase::class.java).forEach { type ->
        type.getDeclaredField("db").apply {
            isAccessible = true
            (get(null) as? RoomDatabase)?.close()
            set(null, null)
        }
    }
}

/**
 * Adds a conversation to the app's cached list, as loading the inbox does. The fake message store cannot produce
 * conversations itself.
 */
internal fun Context.cacheConversation(threadId: Long, archived: Boolean = false) {
    val conversation = Conversation(
        threadId = threadId, snippet = "", date = 1_000, read = true, title = "", photoUri = "",
        isGroupConversation = false, phoneNumber = "", isArchived = archived
    )
    conversationsDB.insertOrUpdate(conversation)
}
