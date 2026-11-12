package org.fossify.messages.adapters

import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemSearchHeadingBinding
import org.fossify.messages.databinding.ItemSearchResultBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.showSelectedCount
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult

class SearchResultsAdapter(
    activity: SimpleActivity,
    var searchResults: ArrayList<SearchResult>,
    recyclerView: MyRecyclerView,
    highlightText: String,
    private val deleteMessages: (messages: List<Message>) -> Unit = {},
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var fontSize = activity.getTextSize()
    private var textToHighlight = highlightText

    // The results under headings: conversations whose name or number matches, then messages whose text matches.
    private var rows = rowsOf(searchResults)

    private fun rowsOf(results: List<SearchResult>): List<Any> {
        val (messages, conversations) = results.partition { it.message != null }
        val sections = listOf(
            R.string.search_heading_conversations to conversations,
            R.string.search_heading_messages to messages
        )
        return sections
            .filter { it.second.isNotEmpty() }
            .flatMap { (heading, items) -> listOf(resources.getString(heading)) + items }
    }

    // Matching messages can be selected and act on themselves only, as in a conversation. Archiving is the exception:
    // it can only apply to whole conversations, and says so.
    override fun getActionMenuId() = R.menu.cab_search_results

    override fun prepareActionMode(menu: Menu) {
        actMode.showSelectedCount(resources, R.plurals.messages_selected, selectedKeys.size)
        menu.findItem(R.id.cab_share).isVisible = isOneItemSelected()
        val conversations = selectedMessages().map { it.threadId }.distinct().size
        menu.findItem(R.id.cab_archive_conversations).apply {
            isVisible = activity.config.isArchiveAvailable && conversations > 0
            title = resources.getQuantityString(R.plurals.archive_conversations, conversations, conversations)
        }
    }

    private fun selectedMessages() =
        searchResults.filter { selectedKeys.contains(it.hashCode()) }.mapNotNull { it.message }

    override fun actionItemPressed(id: Int) {
        val messages = selectedMessages()
        if (messages.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> activity.copyToClipboard(messages.joinToString("\n\n") { it.body })
            R.id.cab_share -> activity.shareTextIntent(messages.first().body)
            R.id.cab_delete -> askConfirmDelete(messages)
            R.id.cab_archive_conversations -> askConfirmArchive(messages.map { it.threadId }.distinct())
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun askConfirmDelete(messages: List<Message>) {
        val items = resources.getQuantityString(R.plurals.delete_messages, messages.size, messages.size)
        val question = String.format(resources.getString(org.fossify.commons.R.string.deletion_confirmation), items)
        ConfirmationDialog(activity, question) {
            ensureBackgroundThread { deleteMessages(messages) }
            finishActMode()
        }
    }

    private fun askConfirmArchive(threadIds: List<Long>) {
        val items = resources.getQuantityString(R.plurals.delete_conversations, threadIds.size, threadIds.size)
        val question = String.format(resources.getString(R.string.archive_confirmation), items)
        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                threadIds.forEach {
                    activity.updateConversationArchivedStatus(it, true)
                    activity.notificationManager.cancel(it.hashCode())
                }
                refreshConversations()
            }
            finishActMode()
        }
    }

    override fun getSelectableItemCount() = searchResults.count { it.message != null }

    override fun getIsItemSelectable(position: Int) = (rows.getOrNull(position) as? SearchResult)?.message != null

    override fun getItemSelectionKey(position: Int) = (rows.getOrNull(position) as? SearchResult)?.hashCode()

    override fun getItemKeyPosition(key: Int) = rows.indexOfFirst { it is SearchResult && it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int) = if (rows[position] is SearchResult) RESULT else HEADING

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = if (viewType == HEADING) {
            ItemSearchHeadingBinding.inflate(layoutInflater, parent, false).root
        } else {
            ItemSearchResultBinding.inflate(layoutInflater, parent, false).root
        }
        return createViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchResult = rows[position] as? SearchResult
        if (searchResult == null) {
            ItemSearchHeadingBinding.bind(holder.itemView).searchHeading.apply {
                text = rows[position] as String
                setTextColor(properPrimaryColor)
            }
            return
        }

        val isMessage = searchResult.message != null
        holder.bindView(searchResult, allowSingleClick = true, allowLongClick = isMessage) { itemView, _ ->
            setupView(itemView, searchResult)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = rows.size

    fun updateItems(newItems: ArrayList<SearchResult>, highlightText: String = "") {
        if (newItems.hashCode() != searchResults.hashCode()) {
            searchResults = newItems.clone() as ArrayList<SearchResult>
            rows = rowsOf(searchResults)
            textToHighlight = highlightText
            notifyDataSetChanged()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, searchResult: SearchResult) {
        ItemSearchResultBinding.bind(view).apply {
            root.setupViewBackground(activity)
            root.isSelected = selectedKeys.contains(searchResult.hashCode())
            searchResultTitle.apply {
                text = searchResult.title.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            searchResultSnippet.apply {
                text = searchResult.snippet.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            searchResultDate.apply {
                text = searchResult.date
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            SimpleContactsHelper(activity).loadContactImage(searchResult.photoUri, searchResultImage, searchResult.title)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (holder.itemViewType == RESULT && !activity.isDestroyed && !activity.isFinishing) {
            val binding = ItemSearchResultBinding.bind(holder.itemView)
            Glide.with(activity).clear(binding.searchResultImage)
        }
    }

    companion object {
        private const val HEADING = 0
        private const val RESULT = 1
    }
}
