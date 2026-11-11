package org.fossify.messages.helpers

import android.app.Application
import androidx.test.core.app.ApplicationProvider

import org.fossify.messages.databases.FilterDatabase
import org.fossify.messages.databases.MessagesDatabase
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.FilteredMessage
import org.fossify.messages.models.SPAM_FOLDER_ID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Made-up senders and numbers only.
@RunWith(RobolectricTestRunner::class)
class FilterRunTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var store: FakeTelephonyProvider

    @Before
    fun setUp() {
        store = FakeTelephonyProvider.install()
        context.config.blockedKeywords = setOf("opt out")
        // spam from saved contacts is filtered too, so that no contact lookup is needed
        context.config.filterSavedContacts = true
    }

    @After
    fun tearDown() {
        resetDatabases()
    }

    private fun addFolderRule(sender: String): Long {
        val folderId = context.filterRulesDB.insertFolder(FilterFolder(name = "Bank"))
        context.filterRulesDB.insertRule(FilterRule(position = 0, folderId = folderId, sender = sender, keywords = ""))
        return folderId
    }

    private fun setSpamAction(action: Int) {
        context.filterRulesDB.insertRule(context.getSpamRule().copy(action = action))
    }

    private fun run() = context.planFilterRun().also { context.applyFilterRun(it) }

    @Test
    fun movesMessagesToTheirFolderAndBack() = background {
        val folderId = addFolderRule("FNB")
        val sms = store.addSms(1, "FNB", "Purchase of R100", 1_000_000)
        store.addSms(2, "0820000001", "Hello", 1_000_000)

        val first = run()
        assertEquals(listOf(sms), first.toFolders.map { it.id })
        assertEquals(folderId, context.filteredDB.getFolder(folderId).single().folderId)
        assertFalse("running the filters must not mark messages as read", store.isRead(sms))

        assertFalse(context.planFilterRun().hasChanges)

        context.filterRulesDB.getRules().filterNot { it.isSpam }.forEach { context.filterRulesDB.deleteRule(it) }
        val second = run()
        assertEquals(listOf(sms), second.toInbox.map { it.id })
        assertTrue(context.filteredDB.getFiled().isEmpty())
    }

    @Test
    fun leavesRestoredMessagesAlone() = background {
        addFolderRule("FNB")
        val sms = store.addSms(1, "FNB", "Purchase of R100", 1_000_000)
        context.filteredDB.insert(FilteredMessage(sms, false, 1, 1_000, restored = true))

        assertFalse(context.planFilterRun().hasChanges)
    }

    @Test
    fun forgetsEntriesWhoseMessageIsGone() = background {
        context.filteredDB.insert(FilteredMessage(99, false, 1, 1_000))

        assertEquals(listOf(99L), context.planFilterRun().stale.map { it.id })
    }

    @Test
    fun spamRuleArchivesConversationsOnce() = background {
        setSpamAction(SPAM_ACTION_ARCHIVE)
        val spam = store.addSms(5, "0820000002", "Reply STOP to opt out", 1_000_000)
        store.addSms(6, "0820000003", "See you later", 1_000_000)

        val first = run()
        assertEquals(listOf(spam), first.toArchive.map { it.id })
        assertTrue(store.isArchived(5))
        assertFalse(store.isArchived(6))
        assertFalse("running the filters must not mark messages as read", store.isRead(spam))
        assertTrue("an archived message stays in its conversation", context.filteredDB.getFiled().isEmpty())

        assertFalse(context.planFilterRun().hasChanges)

        // a conversation the user unarchived is not archived again
        context.updateConversationArchivedStatus(5, false)
        assertFalse(context.planFilterRun().hasChanges)
    }

    @Test
    fun spamRuleReturnsConversationsItNoLongerMatches() = background {
        setSpamAction(SPAM_ACTION_ARCHIVE)
        store.addSms(5, "0820000002", "Reply STOP to opt out", 1_000_000)
        run()

        setSpamAction(SPAM_ACTION_MOVE)
        val second = run()
        assertEquals(setOf(5L), second.unmatchedArchived)
        assertTrue(context.getSpamArchivedThreads().isEmpty())
        assertEquals(1, context.filteredDB.getFolder(SPAM_FOLDER_ID).size)
    }

    @Test
    fun spamGoesToSpamWhereThePhoneCannotArchive() = background {
        store.removeArchive()
        setSpamAction(SPAM_ACTION_ARCHIVE)
        store.addSms(5, "0820000002", "Reply STOP to opt out", 1_000_000)
        store.addSms(6, "0820000004", "To opt out, reply STOP", 1_000_000)

        run()
        assertFalse(context.config.isArchiveAvailable)
        assertEquals(setOf(5L, 6L), context.filteredDB.getFolder(SPAM_FOLDER_ID).map { it.threadId }.toSet())
        assertTrue(context.getSpamArchivedThreads().isEmpty())
    }
}

/** Runs a test's body off the main thread, where Room may be used, and rethrows what it throws. */
internal fun background(block: () -> Unit) {
    var error: Throwable? = null
    Thread { runCatching(block).onFailure { error = it } }.apply {
        start()
        join()
    }
    error?.let { throw it }
}

/** Closes and forgets the app's Room databases, so that each test starts empty. */
internal fun resetDatabases() {
    listOf(FilterDatabase::class.java, MessagesDatabase::class.java).forEach { type ->
        type.getDeclaredField("db").apply {
        isAccessible = true
        (get(null) as? androidx.room.RoomDatabase)?.close()
        set(null, null)
        }
    }
}
