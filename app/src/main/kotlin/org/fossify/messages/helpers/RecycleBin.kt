package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.messagesDB

// This fork has no recycle bin (see Config.useRecycleBin). Deleting is permanent, and the confirmation says so.

/** Returns any messages left in the recycle bin by an earlier version to their conversations. */
fun Context.restoreRecycledMessages() = ensureBackgroundThread {
    // getArchivedCount counts the recycle bin; clearing it also drops entries whose message is gone
    if (messagesDB.getArchivedCount() > 0) {
        messagesDB.deleteAllFromRecycleBin()
        refreshConversations()
    }
}
