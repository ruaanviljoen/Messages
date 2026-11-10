package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.R
import org.fossify.messages.databases.FilterDatabase
import org.fossify.messages.extensions.markMessageRead
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.Message
import org.fossify.messages.models.SPAM_FOLDER_ID

val Context.filterRulesDB get() = FilterDatabase.getInstance(this).FilterRulesDao()

/**
 * What the filters do with a message: move it to [folderId] ([SPAM_FOLDER_ID] for Spam), or delete it ([deletes], Spam
 * only). [rule] is the rule that matched, null for Spam. [notifies] tells whether a notification is still shown.
 */
data class FilterResult(
    val folderId: Long,
    val deletes: Boolean = false,
    val notifies: Boolean = false,
    val rule: FilterRule? = null
)

/** Whether a rule with this sender and these keywords matches a message. A rule with neither matches nothing. */
fun ruleMatches(sender: String, keywords: List<String>, address: String, body: String): Boolean {
    if (sender.isBlank() && keywords.isEmpty()) {
        return false
    }

    val senderMatches = sender.isBlank() || sender.toSenderKey() == address.toSenderKey()
    val keywordsMatch = keywords.isEmpty() || findSpamKeywords(body, keywords).isNotEmpty()
    return senderMatches && keywordsMatch
}

/**
 * The spam filter and the filter rules, read once. Spam runs first; then the rules run in order and the first one
 * that matches decides the folder. Reuse one instance when checking many messages. Create it off the main thread.
 */
class MessageFilter(context: Context) {
    val spam = SpamFilter(context)
    val rules = context.filterRulesDB.getRules()
    private val ruleKeywords = rules.associate { it.id to it.keywordList() }

    /** What to do with a message from [address], or null to leave it in the inbox. */
    fun filter(address: String, body: String): FilterResult? {
        return if (spam.isSpam(address, body)) spamResult() else filterByRules(address, body)
    }

    fun spamResult() = FilterResult(SPAM_FOLDER_ID, deletes = spam.deletes)

    /** What the rules alone do with a message, or null if none matches. */
    fun filterByRules(address: String, body: String): FilterResult? {
        val rule = rules.firstOrNull { ruleMatches(it.sender, ruleKeywords[it.id].orEmpty(), address, body) }
        return rule?.let { FilterResult(it.folderId, notifies = it.notify, rule = it) }
    }
}

/**
 * Files a received message the filters matched: deletes it, or moves it to its folder and, unless the rule notifies,
 * marks it as read. The caller shows a notification only if [FilterResult.notifies].
 */
fun Context.fileReceivedMessage(message: Message, result: FilterResult) {
    if (result.deletes) {
        deleteFilteredMessage(message)
    } else {
        if (!result.notifies) {
            markMessageRead(message.id, message.isMMS)
        }
        filteredDB.insert(message.toFilteredMessage(folderId = result.folderId))
    }
    refreshMessages()
    refreshConversations()
}

/** The names of all folders by id, Spam included. Run it off the main thread. */
fun Context.getFolderNames(): Map<Long, String> =
    mapOf(SPAM_FOLDER_ID to getString(R.string.spam)) + filterRulesDB.getFolders().associate { it.id to it.name }
