package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityRecycleBinConversationsBinding
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.helpers.deleteFilteredMessage
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.helpers.restoreFromSpam
import org.fossify.messages.helpers.spamDB
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult

/**
 * The Spam folder: the messages the spam filter moved here, newest first.
 * Tapping one offers to restore or delete it.
 */
class SpamActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityRecycleBinConversationsBinding::inflate)
    private var messages = emptyList<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.recycleBinToolbar.title = getString(R.string.spam)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupMaterialScrollListener(scrollingView = binding.conversationsList, topAppBar = binding.recycleBinAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.recycleBinAppbar, NavigationIcon.Arrow)
        loadSpam()
    }

    private fun loadSpam() {
        ensureBackgroundThread {
            val spam = spamDB.getSpam()
            val keys = spam.map { it.id to it.isMMS }.toSet()
            messages = spam.map { it.threadId }.distinct()
                .flatMap { getMessages(it, includeScheduledMessages = false, limit = Int.MAX_VALUE) }
                .filter { (it.id to it.isMMS) in keys }
                .sortedByDescending { it.date }
            val results = messages.mapTo(ArrayList()) { it.toSearchResult() }

            runOnUiThread {
                binding.noConversationsPlaceholder.text = getString(R.string.no_spam)
                binding.noConversationsPlaceholder.beVisibleIf(results.isEmpty())
                binding.conversationsFastscroller.beGoneIf(results.isEmpty())
                val adapter = binding.conversationsList.adapter as? SearchResultsAdapter
                if (adapter == null) {
                    binding.conversationsList.adapter =
                        SearchResultsAdapter(this, results, binding.conversationsList, "") { showMessage(it) }
                } else {
                    adapter.updateItems(results)
                }
            }
        }
    }

    private fun Message.toSearchResult() = SearchResult(
        messageId = id,
        title = senderName.ifEmpty { senderPhoneNumber },
        snippet = body,
        date = millis().formatDateOrTime(this@SpamActivity, hideTimeOnOtherDays = true, showCurrentYear = true),
        threadId = threadId,
        photoUri = senderPhotoUri
    )

    private fun showMessage(result: Any) {
        val message = messages.firstOrNull { it.toSearchResult() == result } ?: return
        ConfirmationAdvancedDialog(
            activity = this,
            message = "${message.senderName.ifEmpty { message.senderPhoneNumber }}\n\n${message.body}",
            positive = R.string.restore_to_inbox,
            negative = org.fossify.commons.R.string.delete
        ) { restore ->
            if (restore) {
                changeMessage { restoreFromSpam(message) }
            } else {
                ConfirmationDialog(this) {
                    changeMessage { deleteFilteredMessage(message) }
                }
            }
        }
    }

    private fun changeMessage(change: () -> Unit) {
        ensureBackgroundThread {
            change()
            refreshMessages()
            refreshConversations()
            loadSpam()
        }
    }
}
