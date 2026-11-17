package org.fossify.messages.activities

import android.app.Activity
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.helpers.getFolderTexts
import org.fossify.messages.helpers.searchFolder
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.FilteredMessage

/**
 * The search in a folder's screen. The folder's message texts are read once, off the main thread, and read again only
 * after [messagesChanged]; typing then only filters. [show] gets the conversations to show, each with the message to
 * open it at, if a search found one; results that a newer search or list replaced are dropped. Use it on the main
 * thread.
 */
class FolderSearch(
    private val activity: Activity,
    private val folderId: Long,
    private val show: (List<Pair<Conversation, Long?>>) -> Unit
) {
    var text = ""
        private set

    private var conversations = emptyList<Conversation>()
    private var request = 0
    private var textsVersion = 0

    // read and written by the searches, under the lock, so that only one of them reads the folder
    private val textsLock = Any()
    private var texts: Map<FilteredMessage, String>? = null
    private var textsReadFor = -1

    fun setConversations(list: List<Conversation>) {
        conversations = list
        search(text)
    }

    fun messagesChanged() {
        textsVersion++
    }

    fun search(newText: String) {
        text = newText
        val number = ++request
        if (newText.isBlank()) {
            show(conversations.map { it to null })
            return
        }

        val all = conversations
        val version = textsVersion
        ensureBackgroundThread {
            val folderTexts = synchronized(textsLock) {
                texts?.takeIf { textsReadFor == version }
                    ?: activity.getFolderTexts(folderId).also {
                        texts = it
                        textsReadFor = version
                    }
            }
            val found = searchFolder(all, folderTexts, newText.trim())
            activity.runOnUiThread {
                if (number == request) {
                    show(found)
                }
            }
        }
    }
}
