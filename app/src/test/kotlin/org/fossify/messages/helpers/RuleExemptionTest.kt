package org.fossify.messages.helpers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.fossify.messages.extensions.config
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Made-up numbers only.
@RunWith(RobolectricTestRunner::class)
class RuleExemptionTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        FakeTelephonyProvider.install()
        context.config.blockedKeywords = setOf("opt out")
        context.config.filterSavedContacts = true
    }

    @After
    fun tearDown() {
        resetDatabases()
    }

    @Test
    fun anyRuleLeavesItsExemptSendersAlone() = background {
        val folderId = context.filterRulesDB.insertFolder(FilterFolder(name = "Deliveries"))
        val rule = FilterRule(
            position = 0, folderId = folderId, sender = "", keywords = "parcel", exemptSenders = "082 000 0001"
        )
        context.filterRulesDB.insertRule(rule)

        val filter = MessageFilter(context)
        assertNull(filter.filter("+27820000001", "Your parcel is here"))
        assertEquals(folderId, filter.filter("0820000002", "Your parcel is here")?.folderId)
    }

    @Test
    fun theOldSpamExemptionsMoveIntoTheSpamRule() = background {
        context.getSpamRule()
        context.config.spamExemptNumbers = setOf("0820000003")

        val spam = context.getSpamRule()
        assertEquals(listOf("0820000003"), spam.exemptList())
        assertTrue("the old setting is emptied", context.config.spamExemptNumbers.isEmpty())
        assertNull(MessageFilter(context).filter("0820000003", "Reply STOP to opt out"))
    }

    @Test
    fun ignoringSavedContactsAppliesOnlyToRulesWithoutASender() = background {
        val folderId = context.filterRulesDB.insertFolder(FilterFolder(name = "Bank"))
        val contact = "0820000001"
        val isContact = { address: String -> address == contact }
        fun filterWith(rule: FilterRule) = run {
            context.filterRulesDB.getRules().filterNot { it.isSpam }.forEach { context.filterRulesDB.deleteRule(it) }
            context.filterRulesDB.insertRule(rule)
            MessageFilter(context, isContact).filter(contact, "Payment received")
        }
        val keywordRule = FilterRule(position = 0, folderId = folderId, sender = "", keywords = "payment")

        assertNull("a keyword rule ignoring contacts skips them", filterWith(keywordRule.copy(applyToContacts = false)))
        assertEquals(folderId, filterWith(keywordRule.copy(applyToContacts = true))?.folderId)
        val senderRule = keywordRule.copy(sender = contact, applyToContacts = false)
        assertEquals("a rule naming the sender still applies to it", folderId, filterWith(senderRule)?.folderId)
    }
}
