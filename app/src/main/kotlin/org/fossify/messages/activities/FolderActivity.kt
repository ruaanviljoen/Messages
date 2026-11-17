package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.FolderConversationsAdapter
import org.fossify.messages.databinding.ActivityFolderBinding
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
 * A folder (Spam or a rule's folder): the conversations that have messages in it. Its search bar and results are the
 * inbox's ([MessageSearchPanel]), limited to the folder.
 */
class FolderActivity : SimpleActivity() {
    override var isSearchBarEnabled = true

    private val binding by viewBinding(ActivityFolderBinding::inflate)
    private val folderId by lazy { intent.getLongExtra(FOLDER_ID, SPAM_FOLDER_ID) }
    private val searchPanel by lazy { MessageSearchPanel(this, binding.folderMenu, binding.searchHolder, folderId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val name = intent.getStringExtra(FOLDER_NAME) ?: getString(R.string.spam)
        title = name
        binding.folderMenu.apply {
            updateHintText(getString(R.string.search_in_folder, name))
            toggleForceArrowBackIcon(true)
            onNavigateBackClickListener = { onBackPressedDispatcher.onBackPressed() }
            setupMenu()
        }
        searchPanel.setup()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupMaterialScrollListener(scrollingView = binding.conversationsList, topAppBar = binding.folderMenu)
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        binding.folderMenu.updateColors()
        updateTextColors(binding.folderCoordinator)
        searchPanel.updateColors()
        loadFolderConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        searchPanel.restore()
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.folderMenu.isSearchOpen) {
            binding.folderMenu.closeSearch()
            true
        } else {
            false
        }
    }

    private fun loadFolderConversations() {
        ensureBackgroundThread {
            val conversations = getFolderConversations(folderId).sortedByDescending { it.date }
            runOnUiThread {
                binding.noConversationsPlaceholder.setTextColor(getProperTextColor())
                binding.noConversationsPlaceholder.beVisibleIf(conversations.isEmpty())
                binding.conversationsFastscroller.beGoneIf(conversations.isEmpty())
                getOrCreateAdapter().updateConversations(ArrayList(conversations))
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun getOrCreateAdapter(): FolderConversationsAdapter {
        return binding.conversationsList.adapter as? FolderConversationsAdapter
            ?: FolderConversationsAdapter(
                activity = this,
                folderId = folderId,
                recyclerView = binding.conversationsList,
                onRefresh = { getOrCreateAdapter().notifyDataSetChanged() },
                itemClick = { openConversation(it as Conversation) }
            ).also { binding.conversationsList.adapter = it }
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
