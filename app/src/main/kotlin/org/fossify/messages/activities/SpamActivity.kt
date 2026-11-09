package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.SpamConversationsAdapter
import org.fossify.messages.databinding.ActivityRecycleBinConversationsBinding
import org.fossify.messages.helpers.IS_SPAM
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.getSpamConversations
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/** The Spam folder: the conversations that have spam. It works like the recycle bin, with the same layout. */
class SpamActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityRecycleBinConversationsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.recycleBinToolbar.title = getString(R.string.spam)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupMaterialScrollListener(scrollingView = binding.conversationsList, topAppBar = binding.recycleBinAppbar)
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.recycleBinAppbar, NavigationIcon.Arrow)
        loadSpamConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun loadSpamConversations() {
        ensureBackgroundThread {
            val conversations = getSpamConversations().sortedByDescending { it.date }
            runOnUiThread {
                binding.noConversationsPlaceholder.text = getString(R.string.no_spam)
                binding.noConversationsPlaceholder.beVisibleIf(conversations.isEmpty())
                binding.conversationsFastscroller.beGoneIf(conversations.isEmpty())
                getOrCreateAdapter().updateConversations(ArrayList(conversations))
            }
        }
    }

    private fun getOrCreateAdapter(): SpamConversationsAdapter {
        return binding.conversationsList.adapter as? SpamConversationsAdapter
            ?: SpamConversationsAdapter(
                activity = this,
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
            putExtra(IS_SPAM, true)
            startActivity(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        loadSpamConversations()
    }
}
