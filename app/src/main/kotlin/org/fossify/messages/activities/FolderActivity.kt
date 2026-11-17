package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
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
    private val searchItem by lazy { binding.recycleBinToolbar.menu.findItem(R.id.search) }

    private val folderSearch by lazy { FolderSearch(this, folderId) { show(it) } }

    // the message to open each found conversation at, while searching
    private var foundMessages = emptyMap<Long, Long>()

    // while the search field is open, Back closes it instead of the screen
    private val closeSearch = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            searchItem.collapseActionView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = intent.getStringExtra(FOLDER_NAME) ?: getString(R.string.spam)
        binding.recycleBinToolbar.title = title
        binding.recycleBinToolbar.inflateMenu(R.menu.menu_folder)
        setupSearch(savedInstanceState?.getString(SEARCH_TEXT).orEmpty())
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SEARCH_TEXT, folderSearch.text)
    }

    private fun loadFolderConversations() {
        ensureBackgroundThread {
            val loaded = getFolderConversations(folderId).sortedByDescending { it.date }
            runOnUiThread { folderSearch.setConversations(loaded) }
        }
    }

    // [restoredText] is the search that was open before the screen was recreated, for example by rotating the phone.
    private fun setupSearch(restoredText: String) {
        onBackPressedDispatcher.addCallback(this, closeSearch)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                closeSearch.isEnabled = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                closeSearch.isEnabled = false
                folderSearch.search("")
                return true
            }
        })
        val searchView = searchItem.actionView as SearchView
        searchView.apply {
            queryHint = getString(R.string.search_in_folder, title)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    folderSearch.search(newText)
                    return true
                }
            })
        }
        if (restoredText.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(restoredText, false)
        }
    }

    private fun show(found: List<Pair<Conversation, Long?>>) {
        val shown = found.map { it.first }
        foundMessages = found.mapNotNull { (conversation, id) -> id?.let { conversation.threadId to it } }.toMap()
        binding.noConversationsPlaceholder.text = if (folderSearch.text.isBlank()) {
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
        private const val SEARCH_TEXT = "search_text"

        fun intent(context: Context, folderId: Long, name: String) = Intent(context, FolderActivity::class.java).apply {
            putExtra(FOLDER_ID, folderId)
            putExtra(FOLDER_NAME, name)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        folderSearch.messagesChanged()
        loadFolderConversations()
    }
}
