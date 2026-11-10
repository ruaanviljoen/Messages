package org.fossify.messages.dialogs

import android.app.Activity
import org.fossify.commons.databinding.DialogMessageBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.messages.R

/** Asks before deleting conversations permanently, and offers to archive them instead when [onArchive] is given. */
class DeleteConversationsDialog(activity: Activity, message: String, onArchive: (() -> Unit)?, onDelete: () -> Unit) {
    init {
        val view = DialogMessageBinding.inflate(activity.layoutInflater)
        view.message.text = message
        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.yes) { _, _ -> onDelete() }
            .setNegativeButton(org.fossify.commons.R.string.no, null)
        if (onArchive != null) {
            builder.setNeutralButton(R.string.archive_instead) { _, _ -> onArchive() }
        }
        activity.setupDialogStuff(view.root, builder)
    }
}
