package org.fossify.messages.helpers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.fossify.messages.extensions.config
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.FilteredMessage
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
class AutoDeleteTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var store: FakeTelephonyProvider
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        store = FakeTelephonyProvider.install()
        context.config.blockedKeywords = emptySet()
        context.config.filterSavedContacts = true
    }

    @After
    fun tearDown() {
        resetDatabases()
    }

    private fun addRule(deleteAfterDays: Int): FilterRule {
        val folderId = context.filterRulesDB.insertFolder(FilterFolder(name = "Deliveries"))
        val rule = FilterRule(position = 0, folderId = folderId, sender = "", keywords = "parcel")
        return rule.copy(id = context.filterRulesDB.insertRule(rule.copy(deleteAfterDays = deleteAfterDays)))
    }

    private fun run() = context.planFilterRun().also { context.applyFilterRun(it) }

    @Test
    fun runDeletesMessagesOlderThanTheRuleKeepsThem() = background {
        val rule = addRule(deleteAfterDays = 2)
        val old = store.addSms(1, "COURIER", "Your parcel is on its way", now - 3 * day)
        val recent = store.addSms(2, "COURIER", "Your parcel arrives today", now - day)

        val run = run()
        assertEquals(listOf(old), run.toExpire.map { it.id })
        assertFalse(store.exists(old))
        assertTrue(store.exists(recent))
        assertEquals(listOf(recent), context.filteredDB.getFolder(rule.folderId).map { it.id })
    }

    @Test
    fun rulesThatKeepMessagesDeleteNothing() = background {
        addRule(deleteAfterDays = 0)
        val old = store.addSms(1, "COURIER", "Your parcel is on its way", now - 300 * day)

        assertTrue(run().toExpire.isEmpty())
        assertTrue(store.exists(old))
    }

    @Test
    fun filedMessagesAreDeletedOnceTheyExpire() = background {
        val rule = addRule(deleteAfterDays = 2)
        val old = store.addSms(1, "COURIER", "Your parcel is on its way", now - 3 * day)
        val restored = store.addSms(2, "COURIER", "Your parcel was delivered", now - 3 * day)
        val oldDate = ((now - 3 * day) / 1000).toInt()
        context.filteredDB.insert(FilteredMessage(old, false, 1, oldDate, folderId = rule.folderId))
        val restoredEntry = FilteredMessage(restored, false, 2, oldDate, restored = true, folderId = rule.folderId)
        context.filteredDB.insert(restoredEntry)

        val recent = store.addSms(3, "COURIER", "Your parcel arrives today", now - day)
        val recentDate = ((now - day) / 1000).toInt()
        context.filteredDB.insert(FilteredMessage(recent, false, 3, recentDate, folderId = rule.folderId))

        context.deleteExpiredMessages()
        assertFalse(store.exists(old))
        assertTrue("a message younger than the rule allows is kept", store.exists(recent))
        assertTrue("a message restored to the inbox is kept", store.exists(restored))
    }

    @Test
    fun messagesStayWhenTheirRuleIsDeleted() = background {
        val rule = addRule(deleteAfterDays = 2)
        val old = store.addSms(1, "COURIER", "Your parcel is on its way", now - 3 * day)
        val oldDate = ((now - 3 * day) / 1000).toInt()
        context.filteredDB.insert(FilteredMessage(old, false, 1, oldDate, folderId = rule.folderId))
        context.filterRulesDB.deleteRule(rule)

        context.deleteExpiredMessages()
        assertTrue(store.exists(old))
    }

    @Test
    fun spamThatIsArchivedIsNeverDeleted() = background {
        context.config.blockedKeywords = setOf("opt out")
        val spam = context.getSpamRule().copy(action = SPAM_ACTION_ARCHIVE, deleteAfterDays = 2)
        context.filterRulesDB.insertRule(spam)
        assertFalse(spam.expired(((now - 300 * day) / 1000).toInt()))
    }
}
