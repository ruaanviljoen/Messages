package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.FolderConversationsAdapter
import org.fossify.messages.databinding.ActivityRecycleBinConversationsBinding
import org.fossify.messages.helpers.FOLDER_ID
import org.fossify.messages.helpers.FOLDER_NAME
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.getFolderConversations
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.SPAM_FOLDER_ID
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * A folder (Spam or a rule's folder): the conversations that have messages in it. It works like the recycle bin, with
 * the same layout.
 */
class FolderActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityRecycleBinConversationsBinding::inflate)
    private val folderId by lazy { intent.getLongExtra(FOLDER_ID, SPAM_FOLDER_ID) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = intent.getStringExtra(FOLDER_NAME) ?: getString(R.string.spam)
        binding.recycleBinToolbar.title = title
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupMaterialScrollListener(scrollingView = binding.conversationsList, topAppBar = binding.recycleBinAppbar)
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.recycleBinAppbar, NavigationIcon.Arrow)
        loadFolderConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun loadFolderConversations() {
        ensureBackgroundThread {
            val conversations = getFolderConversations(folderId).sortedByDescending { it.date }
            runOnUiThread {
                binding.noConversationsPlaceholder.text = getString(R.string.no_messages_in_folder)
                binding.noConversationsPlaceholder.beVisibleIf(conversations.isEmpty())
                binding.conversationsFastscroller.beGoneIf(conversations.isEmpty())
                getOrCreateAdapter().updateConversations(ArrayList(conversations))
            }
        }
    }

    private fun getOrCreateAdapter(): FolderConversationsAdapter {
        return binding.conversationsList.adapter as? FolderConversationsAdapter
            ?: FolderConversationsAdapter(
                activity = this,
                folderId = folderId,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { openConversation(it as Conversation) }
            ).also { binding.conversationsList.adapter = it }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateAdapter().notifyDataSetChanged()
    }

    private fun openConversation(conversation: Conversation) {
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            putExtra(FOLDER_ID, folderId)
            startActivity(this)
        }
    }

    companion object {
        fun intent(context: Context, folderId: Long, name: String) = Intent(context, FolderActivity::class.java).apply {
            putExtra(FOLDER_ID, folderId)
            putExtra(FOLDER_NAME, name)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        loadFolderConversations()
    }
}
