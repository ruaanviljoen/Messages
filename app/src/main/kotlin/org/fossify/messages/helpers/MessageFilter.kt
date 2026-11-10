package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.R
import org.fossify.messages.databases.FilterDatabase
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.markMessageRead
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.Message
import org.fossify.messages.models.SPAM_FOLDER_ID

val Context.filterRulesDB get() = FilterDatabase.getInstance(this).FilterRulesDao()

/**
 * What the filters do with a message: move it to [folderId] ([SPAM_FOLDER_ID] for Spam), or leave it in its
 * conversation and archive the conversation ([archives]).
 * [rule] is the rule that matched and [keywords] the keywords of it that the message contains. [notifies] tells whether
 * a notification is still shown.
 */
data class FilterResult(
    val folderId: Long,
    val rule: FilterRule,
    val keywords: List<String>,
    val archives: Boolean = false,
    val notifies: Boolean = false
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

private val spamRuleLock = Any()

/**
 * The Spam rule, created from the Spam settings the first time it is needed. Locked, so that messages arriving at the
 * same moment cannot create it twice. Run it off the main thread.
 */
fun Context.getSpamRule(): FilterRule = synchronized(spamRuleLock) {
    filterRulesDB.getSpamRule()?.let { return it }

    val rule = FilterRule(
        position = -1,
        folderId = SPAM_FOLDER_ID,
        sender = "",
        keywords = config.blockedKeywords.sorted().joinToString("\n"),
        action = config.spamAction,
        isSpam = true,
        applyToContacts = config.filterSavedContacts
    )
    return rule.copy(id = filterRulesDB.insertRule(rule))
}

/**
 * The filter rules, read once: the Spam rule first, then the others in order; the first rule that matches decides
 * what happens. Reuse one instance when checking many messages. Create it off the main thread.
 */
class MessageFilter(private val context: Context) {
    // getSpamRule creates the Spam rule if it does not exist yet
    val rules = run {
        context.getSpamRule()
        context.filterRulesDB.getRules()
    }
    private val ruleKeywords = rules.associate { it.id to it.keywordList() }
    private val spamExemptNumbers = context.config.spamExemptNumbers.map { it.toSenderKey() }.toSet()

    // where the phone cannot archive, the Spam rule moves to Spam instead
    private val archiveAvailable = context.config.isArchiveAvailable
    private val savedContacts = HashMap<String, Boolean>()

    /** What to do with a message from [address], or null to leave it in the inbox. */
    fun filter(address: String, body: String): FilterResult? {
        rules.forEach { rule ->
            if (applies(rule, address, body)) {
                val keywords = findSpamKeywords(body, ruleKeywords[rule.id].orEmpty())
                val archives = rule.archives() && archiveAvailable
                // an archived message is never notified about
                val notifies = rule.notify && !archives
                return FilterResult(rule.folderId, rule, keywords, archives = archives, notifies = notifies)
            }
        }
        return null
    }

    private fun applies(rule: FilterRule, address: String, body: String) =
        ruleMatches(rule.sender, ruleKeywords[rule.id].orEmpty(), address, body) &&
            !(rule.isSpam && address.toSenderKey() in spamExemptNumbers) &&
            (rule.applyToContacts || !isSavedContact(address))

    private fun isSavedContact(address: String) = savedContacts.getOrPut(address) {
        SimpleContactsHelper(context).existsSync(address) == ContactLookupResult.Found
    }
}

/**
 * Files a received message the filters matched: moves it to its folder, or archives its conversation. Unless the rule
 * notifies, the message is marked as read. The caller shows a notification only if [FilterResult.notifies].
 */
fun Context.fileReceivedMessage(message: Message, result: FilterResult) {
    if (!result.notifies) {
        markMessageRead(message.id, message.isMMS)
    }
    // if the conversation cannot be archived, the message goes to the rule's folder, which is Spam
    if (!result.archives || !archiveSpamConversation(message.threadId)) {
        filteredDB.insert(message.toFilteredMessage(folderId = result.folderId))
    }
    refreshMessages()
    refreshConversations()
}

/** The names of all folders by id, Spam included. Run it off the main thread. */
fun Context.getFolderNames(): Map<Long, String> =
    mapOf(SPAM_FOLDER_ID to getString(R.string.spam)) + filterRulesDB.getFolders().associate { it.id to it.name }
