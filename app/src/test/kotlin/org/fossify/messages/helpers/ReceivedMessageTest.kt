package org.fossify.messages.helpers

import android.app.Application
import android.provider.Telephony.Sms
import androidx.test.core.app.ApplicationProvider
import org.fossify.messages.extensions.config
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.Message
import org.fossify.messages.models.SPAM_FOLDER_ID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Made-up senders and numbers only.
@RunWith(RobolectricTestRunner::class)
class ReceivedMessageTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var store: FakeTelephonyProvider

    @Before
    fun setUp() {
        store = FakeTelephonyProvider.install()
        context.config.blockedKeywords = setOf("opt out")
        context.config.filterSavedContacts = true
    }

    @After
    fun tearDown() {
        resetDatabases()
    }

    // Saves an SMS and files it as the receivers do; the receivers' notification and inbox steps are not run.
    private fun receive(threadId: Long, address: String, body: String): Pair<Long, FilterResult?> {
        val id = store.addSms(threadId, address, body, 1_000_000)
        val message = Message(
            id = id, body = body, type = Sms.MESSAGE_TYPE_INBOX, status = Sms.STATUS_NONE, participants = ArrayList(),
            date = 1_000, read = false, threadId = threadId, isMMS = false, attachment = null,
            senderPhoneNumber = address, senderName = address, senderPhotoUri = "", subscriptionId = -1
        )
        val result = MessageFilter(context).filter(address, body)
        result?.let { context.fileReceivedMessage(message, it) }
        return id to result
    }

    @Test
    fun leavesOtherMessagesAlone() = background {
        val (id, result) = receive(1, "0820000001", "Hello")
        assertNull(result)
        assertFalse(store.isRead(id))
    }

    @Test
    fun filesToTheRuleFolder() = background {
        val folderId = context.filterRulesDB.insertFolder(FilterFolder(name = "Bank"))
        val rule = FilterRule(position = 0, folderId = folderId, sender = "BANK", keywords = "", notify = true)
        context.filterRulesDB.insertRule(rule)

        val (id, result) = receive(1, "BANK", "Purchase of R100")
        assertTrue(result!!.notifies)
        assertFalse("filtering never marks a message as read", store.isRead(id))
        assertEquals(listOf(id), context.filteredDB.getFolder(folderId).map { it.id })
    }

    @Test
    fun spamGoesToSpamUnread() = background {
        val (id, result) = receive(5, "0820000002", "Reply STOP to opt out")
        assertFalse(result!!.notifies)
        assertFalse("filtering never marks a message as read", store.isRead(id))
        assertEquals(listOf(id), context.filteredDB.getFolder(SPAM_FOLDER_ID).map { it.id })
    }

    @Test
    fun spamRuleArchivesTheConversation() = background {
        context.filterRulesDB.insertRule(context.getSpamRule().copy(action = SPAM_ACTION_ARCHIVE))

        val (id, result) = receive(5, "0820000002", "Reply STOP to opt out")
        assertFalse(result!!.notifies)
        assertFalse("filtering never marks a message as read", store.isRead(id))
        assertTrue(store.isArchived(5))
        assertTrue("an archived message stays in its conversation", context.filteredDB.getFiled().isEmpty())
        assertEquals(setOf(5L), context.getSpamArchivedThreads())
    }

    @Test
    fun spamGoesToSpamWhereThePhoneCannotArchive() = background {
        store.removeArchive()
        context.filterRulesDB.insertRule(context.getSpamRule().copy(action = SPAM_ACTION_ARCHIVE))

        val (first, _) = receive(5, "0820000002", "Reply STOP to opt out")
        val (second, _) = receive(6, "0820000004", "To opt out, reply STOP")
        assertEquals(listOf(first, second), context.filteredDB.getFolder(SPAM_FOLDER_ID).map { it.id }.sorted())
        assertTrue(context.getSpamArchivedThreads().isEmpty())
    }
}
