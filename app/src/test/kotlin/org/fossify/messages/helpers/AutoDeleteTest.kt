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

    // [changedDaysAgo] is when the rules last changed
    private fun addRule(deleteAfterDays: Int, changedDaysAgo: Int = 0): FilterRule {
        val folderId = context.filterRulesDB.insertFolder(FilterFolder(name = "Deliveries"))
        val rule = FilterRule(
            position = 0, folderId = folderId, sender = "", keywords = "parcel", deleteAfterDays = deleteAfterDays
        )
        context.config.filtersChangedAt = (now - changedDaysAgo * day) / 1000
        return rule.copy(id = context.filterRulesDB.insertRule(rule))
    }

    // Saves a message received [daysAgo] and files it to the rule's folder.
    private fun addFiled(threadId: Long, daysAgo: Int, rule: FilterRule, restored: Boolean = false): Long {
        val id = store.addSms(threadId, "COURIER", "Your parcel update", now - daysAgo * day)
        val date = ((now - daysAgo * day) / 1000).toInt()
        context.filteredDB.insert(FilteredMessage(id, false, threadId, date, restored, rule.folderId))
        return id
    }

    private fun run() = context.planFilterRun().also { context.applyFilterRun(it) }

    @Test
    fun runDeletesMessagesOlderThanTheRuleKeepsThem() = background {
        val rule = addRule(deleteAfterDays = 2)
        val old = store.addSms(1, "COURIER", "Your parcel is on its way", now - 3 * day)
        val recent = store.addSms(2, "COURIER", "Your parcel arrives today", now - day)

        val run = run()
        assertEquals(listOf(old), run.toExpire.map { it.id })
        assertEquals("the old message was still in the inbox", 1, run.expiringFromInbox)
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
        val rule = addRule(deleteAfterDays = 2, changedDaysAgo = 5)
        val old = addFiled(1, daysAgo = 3, rule = rule)
        val restored = addFiled(2, daysAgo = 3, rule = rule, restored = true)
        val recent = addFiled(3, daysAgo = 1, rule = rule)

        context.deleteExpiredMessages()
        assertFalse(store.exists(old))
        assertTrue("a message younger than the rule allows is kept", store.exists(recent))
        assertTrue("a message restored to the inbox is kept", store.exists(restored))
    }

    @Test
    fun messagesStayWhenTheirRuleIsDeleted() = background {
        val rule = addRule(deleteAfterDays = 2, changedDaysAgo = 5)
        val old = addFiled(1, daysAgo = 3, rule = rule)
        context.filterRulesDB.deleteRule(rule)

        context.deleteExpiredMessages()
        assertTrue(store.exists(old))
    }

    @Test
    fun messagesAlreadyTooOldWhenTheRulesChangeNeedAConfirmedRun() = background {
        // the rules change now, and the run that offered to delete the old message was cancelled
        val rule = addRule(deleteAfterDays = 2)
        val old = addFiled(1, daysAgo = 30, rule = rule)

        context.deleteExpiredMessages()
        assertTrue("cancelling the run keeps the message", store.exists(old))

        assertEquals(listOf(old), run().toExpire.map { it.id })
        assertFalse("a confirmed run deletes it", store.exists(old))
    }

    @Test
    fun aRunCanBeAppliedWithoutDeleting() = background {
        val rule = addRule(deleteAfterDays = 2)
        val old = store.addSms(1, "COURIER", "Your parcel is on its way", now - 3 * day)

        val run = context.planFilterRun()
        assertEquals(listOf(old), run.toExpire.map { it.id })
        context.applyFilterRun(run.withoutDeleting())
        assertTrue(store.exists(old))
        assertEquals("it is filed instead", listOf(old), context.filteredDB.getFolder(rule.folderId).map { it.id })
    }

    @Test
    fun spamThatIsArchivedIsNeverDeleted() = background {
        context.config.blockedKeywords = setOf("opt out")
        val spam = context.getSpamRule().copy(action = SPAM_ACTION_ARCHIVE, deleteAfterDays = 2)
        context.filterRulesDB.insertRule(spam)
        assertFalse(spam.expired(((now - 300 * day) / 1000).toInt()))
    }
}
