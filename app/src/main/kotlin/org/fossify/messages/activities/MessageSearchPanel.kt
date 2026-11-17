package org.fossify.messages.activities

import android.content.Intent
import android.text.TextUtils
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MySearchMenu
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.SearchResultsBinding
import org.fossify.messages.helpers.FOLDER_ID
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.SearchFindings
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.deleteMessagesFromSearch
import org.fossify.messages.helpers.findForSearch
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult

/**
 * The search shared by the inbox and the folders. While the [menu] has text, it shows the results in [views] with the
 * same headings, selection menu and folder labels everywhere. [folderId] limits it to the messages in one folder and
 * the conversations with messages there; null searches everything.
 */
class MessageSearchPanel(
    private val activity: SimpleActivity,
    private val menu: MySearchMenu,
    private val views: SearchResultsBinding,
    private val folderId: Long? = null
) {
    private var lastSearchedText = ""
    private var findings: SearchFindings? = null

    /** Shows the results while the search has text, and hides them when it closes. */
    fun setup() {
        menu.onSearchClosedListener = { fadeOut() }
        menu.onSearchTextChangedListener = { text ->
            if (text.isNotEmpty()) {
                if (views.root.alpha < 1f) {
                    views.root.fadeIn()
                }
            } else {
                fadeOut()
            }
            search(text)
        }
    }

    fun updateColors() = views.root.setBackgroundColor(activity.getProperBackgroundColor())

    /**
     * The search text survives a rotation, but the open search does not. Reopens it as a tap on the search box does
     * (focus opens it), after the search menu has set up its focus listener, and repeats the search. Call it from
     * onRestoreInstanceState.
     */
    fun restore() {
        val query = menu.getCurrentQuery()
        if (query.isNotEmpty() || menu.binding.topToolbarSearch.hasFocus()) {
            menu.post {
                activity.currentFocus?.clearFocus()
                menu.focusView()
                if (query.isNotEmpty()) {
                    views.root.alpha = 1f
                    views.root.beVisible()
                    search(query, forceUpdate = true)
                }
            }
        }
    }

    private fun fadeOut() {
        views.root.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                views.root.beGone()
                search("", forceUpdate = true)
            }.start()
    }

    private fun search(text: String, forceUpdate: Boolean = false) {
        if (!menu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        views.searchPlaceholder2.beGoneIf(text.length >= MIN_SEARCH_LENGTH)
        if (text.length >= MIN_SEARCH_LENGTH) {
            ensureBackgroundThread {
                val found = activity.findForSearch(text, folderId)
                val results = toResults(found)
                activity.runOnUiThread {
                    if (text == lastSearchedText) {
                        findings = found
                        show(results, text)
                    }
                }
            }
        } else {
            views.searchPlaceholder.beVisible()
            views.searchResultsList.beGone()
        }
    }

    // Conversations whose name matches first, then the matching messages, newest first. Outside a folder, a message
    // in a folder is labelled with the folder's name.
    private fun toResults(found: SearchFindings): ArrayList<SearchResult> {
        val results = ArrayList<SearchResult>()
        found.conversations.forEach { conversation ->
            results += SearchResult(
                messageId = -1,
                title = conversation.title,
                snippet = conversation.phoneNumber,
                date = formatDate(conversation.date),
                threadId = conversation.threadId,
                photoUri = conversation.photoUri
            )
        }

        found.messages.sortedByDescending { it.date }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                recipient = TextUtils.join(", ", message.participants.map { it.name })
            }

            val folderName = found.folders[message.id to message.isMMS]?.let { found.folderNames[it] }
            results += SearchResult(
                messageId = message.id,
                title = if (folderName != null) "$recipient · $folderName" else recipient,
                snippet = message.body,
                date = formatDate(message.date),
                threadId = message.threadId,
                photoUri = message.senderPhotoUri,
                message = message
            )
        }
        return results
    }

    private fun formatDate(seconds: Int) =
        (seconds * 1000L).formatDateOrTime(context = activity, hideTimeOnOtherDays = true, showCurrentYear = true)

    private fun show(results: ArrayList<SearchResult>, text: String) {
        views.searchResultsList.beVisibleIf(results.isNotEmpty())
        views.searchPlaceholder.beVisibleIf(results.isEmpty())

        val adapter = views.searchResultsList.adapter as? SearchResultsAdapter
        if (adapter != null) {
            adapter.updateItems(results, text)
            return
        }

        views.searchResultsList.adapter = SearchResultsAdapter(
            activity = activity,
            searchResults = results,
            recyclerView = views.searchResultsList,
            highlightText = text,
            deleteMessages = { delete(it) }
        ) { open(it as SearchResult) }
    }

    // A result in a folder opens the conversation's messages in that folder.
    private fun open(result: SearchResult) {
        activity.hideKeyboard()
        val folder = folderId ?: result.message?.let { findings?.folders?.get(it.id to it.isMMS) }
        Intent(activity, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, result.threadId)
            putExtra(THREAD_TITLE, result.title)
            putExtra(SEARCHED_MESSAGE_ID, result.messageId)
            folder?.let { putExtra(FOLDER_ID, it) }
            activity.startActivity(this)
        }
    }

    // Deletes messages selected in the results, as the conversation screen does, and repeats the search.
    private fun delete(messages: List<Message>) {
        activity.deleteMessagesFromSearch(messages)
        activity.runOnUiThread {
            if (menu.isSearchOpen) {
                search(lastSearchedText, forceUpdate = true)
            }
        }
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
    }
}
