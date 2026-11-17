package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.widget.SearchView
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
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.getFolderConversations
import org.fossify.messages.helpers.getFolderTexts
import org.fossify.messages.helpers.searchFolder
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.FilteredMessage
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
    private val searchItem by lazy { binding.recycleBinToolbar.menu.findItem(R.id.search) }

    // The folder's conversations, and while searching the text, the folder's message texts and the message to open
    // each found conversation at.
    private var conversations = emptyList<Conversation>()
    private var searchText = ""
    @Volatile
    private var folderTexts: Map<FilteredMessage, String>? = null
    private var foundMessages = emptyMap<Long, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = intent.getStringExtra(FOLDER_NAME) ?: getString(R.string.spam)
        binding.recycleBinToolbar.title = title
        binding.recycleBinToolbar.inflateMenu(R.menu.menu_folder)
        setupSearch()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupMaterialScrollListener(scrollingView = binding.conversationsList, topAppBar = binding.recycleBinAppbar)
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.recycleBinAppbar, NavigationIcon.Arrow, searchMenuItem = searchItem)
        loadFolderConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun loadFolderConversations() {
        ensureBackgroundThread {
            val loaded = getFolderConversations(folderId).sortedByDescending { it.date }
            runOnUiThread {
                conversations = loaded
                // the messages in the folder may have changed
                folderTexts = null
                search(searchText)
            }
        }
    }

    private fun setupSearch() {
        (searchItem.actionView as SearchView).apply {
            queryHint = getString(R.string.search_in_folder, title)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    search(newText)
                    return true
                }
            })
        }
    }

    // Shows the conversations that match [text], or all of them when it is empty. The folder's message texts are read
    // once per search, off the main thread.
    private fun search(text: String) {
        searchText = text
        if (text.isBlank()) {
            show(conversations, emptyMap())
            return
        }

        val all = conversations
        ensureBackgroundThread {
            val texts = folderTexts ?: getFolderTexts(folderId).also { folderTexts = it }
            val found = searchFolder(all, texts, text.trim())
            runOnUiThread {
                if (searchText == text) {
                    show(found.map { it.first }, found.mapNotNull { (c, id) -> id?.let { c.threadId to it } }.toMap())
                }
            }
        }
    }

    private fun show(shown: List<Conversation>, messages: Map<Long, Long>) {
        foundMessages = messages
        binding.noConversationsPlaceholder.text = if (searchText.isBlank()) {
            getString(R.string.no_messages_in_folder)
        } else {
            getString(org.fossify.commons.R.string.no_items_found)
        }
        binding.noConversationsPlaceholder.beVisibleIf(shown.isEmpty())
        binding.conversationsFastscroller.beGoneIf(shown.isEmpty())
        getOrCreateAdapter().updateConversations(ArrayList(shown))
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
            foundMessages[conversation.threadId]?.let { putExtra(SEARCHED_MESSAGE_ID, it) }
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
