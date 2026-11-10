package org.fossify.messages.adapters

import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemSearchResultBinding
import org.fossify.messages.dialogs.DeleteConfirmationDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult

class SearchResultsAdapter(
    activity: SimpleActivity,
    var searchResults: ArrayList<SearchResult>,
    recyclerView: MyRecyclerView,
    highlightText: String,
    private val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean) -> Unit = { _, _ -> },
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var fontSize = activity.getTextSize()
    private var textToHighlight = highlightText

    // Matching messages can be selected and act on themselves only, as in a conversation.
    override fun getActionMenuId() = R.menu.cab_search_results

    override fun prepareActionMode(menu: Menu) {
        menu.findItem(R.id.cab_share).isVisible = isOneItemSelected()
    }

    override fun actionItemPressed(id: Int) {
        val messages = searchResults.filter { selectedKeys.contains(it.hashCode()) }.mapNotNull { it.message }
        if (messages.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> activity.copyToClipboard(messages.joinToString("\n\n") { it.body })
            R.id.cab_share -> activity.shareTextIntent(messages.first().body)
            R.id.cab_delete -> askConfirmDelete(messages)
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun askConfirmDelete(messages: List<Message>) {
        val items = resources.getQuantityString(R.plurals.delete_messages, messages.size, messages.size)
        val useRecycleBin = activity.config.useRecycleBin
        val baseString = if (useRecycleBin) {
            org.fossify.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            org.fossify.commons.R.string.deletion_confirmation
        }
        DeleteConfirmationDialog(activity, String.format(resources.getString(baseString), items), useRecycleBin) {
            val toRecycleBin = useRecycleBin && !it
            ensureBackgroundThread { deleteMessages(messages, toRecycleBin) }
            finishActMode()
        }
    }

    override fun getSelectableItemCount() = searchResults.count { it.message != null }

    override fun getIsItemSelectable(position: Int) = searchResults.getOrNull(position)?.message != null

    override fun getItemSelectionKey(position: Int) = searchResults.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = searchResults.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchResult = searchResults[position]
        val isMessage = searchResult.message != null
        holder.bindView(searchResult, allowSingleClick = true, allowLongClick = isMessage) { itemView, _ ->
            setupView(itemView, searchResult)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = searchResults.size

    fun updateItems(newItems: ArrayList<SearchResult>, highlightText: String = "") {
        if (newItems.hashCode() != searchResults.hashCode()) {
            searchResults = newItems.clone() as ArrayList<SearchResult>
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
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = ItemSearchResultBinding.bind(holder.itemView)
            Glide.with(activity).clear(binding.searchResultImage)
        }
    }
}
