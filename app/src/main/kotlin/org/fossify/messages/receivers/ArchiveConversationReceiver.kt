package org.fossify.messages.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.archiveConversationForUser
import org.fossify.messages.helpers.refreshConversations

/**
 * The Archive button on a message notification: marks the conversation read, as "Mark as read" does, and archives it.
 */
class ArchiveConversationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        if (threadId == 0L) {
            return
        }

        context.notificationManager.cancel(threadId.hashCode())
        val pending = goAsync()
        ensureBackgroundThread {
            try {
                context.markThreadMessagesRead(threadId)
                context.conversationsDB.markRead(threadId)
                if (!context.archiveConversationForUser(threadId)) {
                    context.toast(R.string.archive_not_available)
                }
                refreshConversations()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Adds the Archive button, where the phone can archive. */
fun NotificationCompat.Builder.addArchiveAction(context: Context, threadId: Long, notificationId: Int) {
    if (!context.config.isArchiveAvailable) {
        return
    }

    val intent = Intent(context, ArchiveConversationReceiver::class.java).putExtra(THREAD_ID, threadId)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    addAction(R.drawable.ic_archive_vector, context.getString(R.string.archive), pendingIntent)
}
