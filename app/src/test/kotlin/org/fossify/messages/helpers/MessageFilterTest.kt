package org.fossify.messages.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFilterTest {
    private val payments = listOf("purchase", "payment")

    @Test
    fun matchesSenderAndKeywordsTogether() {
        assertTrue(ruleMatches("FNB", payments, "FNB", "Purchase of R250 at a shop"))
        assertFalse(ruleMatches("FNB", payments, "FNB", "Your OTP is 123456"))
        assertFalse(ruleMatches("FNB", payments, "Absa", "Purchase of R250 at a shop"))
    }

    @Test
    fun matchesSenderAlone() {
        assertTrue(ruleMatches("fnb", emptyList(), "FNB", "Anything"))
        assertTrue(ruleMatches("0820000001", emptyList(), "+27820000001", "Anything"))
        assertFalse(ruleMatches("0820000001", emptyList(), "+27820000002", "Anything"))
    }

    @Test
    fun matchesKeywordsAlone() {
        assertTrue(ruleMatches("", payments, "Anyone", "Your PAYMENT was received"))
        assertFalse(ruleMatches(" ", payments, "Anyone", "Your OTP is 123456"))
    }

    @Test
    fun ruleWithoutSenderOrKeywordsMatchesNothing() {
        assertFalse(ruleMatches("", emptyList(), "FNB", "Purchase"))
    }
}
