package org.fossify.messages.adapters

import android.view.Menu
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.helpers.deleteConversationFromFolder
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.helpers.restoreConversationFromFolder
import org.fossify.messages.helpers.showSelectedCount

/** The conversations in a folder. Restoring or deleting acts only on the conversation's messages in the folder. */
class FolderConversationsAdapter(
    activity: SimpleActivity,
    private val folderId: Long,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_recycle_bin_conversations

    override fun prepareActionMode(menu: Menu) {
        actMode.showSelectedCount(resources, R.plurals.conversations_selected, selectedKeys.size)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> confirmAndChange(org.fossify.commons.R.string.deletion_confirmation) {
                activity.deleteConversationFromFolder(it, folderId)
            }

            R.id.cab_restore -> confirmAndChange(R.string.restore_confirmation) {
                activity.restoreConversationFromFolder(it, folderId)
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
