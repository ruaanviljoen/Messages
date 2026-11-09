package org.fossify.messages.adapters

import android.view.Menu
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.helpers.deleteConversationSpam
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.helpers.restoreConversationFromSpam

/** The conversations in the Spam folder. Restoring or deleting acts only on a conversation's spam. */
class SpamConversationsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_recycle_bin_conversations

    override fun prepareActionMode(menu: Menu) = Unit

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> confirmAndChange(org.fossify.commons.R.string.deletion_confirmation) {
                activity.deleteConversationSpam(it)
            }

            R.id.cab_restore -> confirmAndChange(R.string.restore_confirmation) {
                activity.restoreConversationFromSpam(it)
            }

            R.id.cab_select_all -> selectAll()
        }
    }

    private fun confirmAndChange(questionId: Int, change: (threadId: Long) -> Unit) {
        val items = resources.getQuantityString(R.plurals.delete_conversations, selectedKeys.size, selectedKeys.size)
        val selected = currentList.filter { selectedKeys.contains(it.hashCode()) }
        ConfirmationDialog(activity, String.format(resources.getString(questionId), items)) {
            ensureBackgroundThread {
                selected.forEach { change(it.threadId) }
                refreshMessages()
                refreshConversations()
                activity.runOnUiThread { finishActMode() }
            }
        }
    }
}
