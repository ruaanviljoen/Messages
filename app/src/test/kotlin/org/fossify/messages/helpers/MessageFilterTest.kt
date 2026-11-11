package org.fossify.messages.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFilterTest {
    private val payments = listOf("purchase", "payment")

    @Test
    fun matchesSenderAndKeywordsTogether() {
        assertTrue(ruleMatches("BANK", payments, "BANK", "Purchase of R250 at a shop"))
        assertFalse(ruleMatches("BANK", payments, "BANK", "Your OTP is 123456"))
        assertFalse(ruleMatches("BANK", payments, "SHOP", "Purchase of R250 at a shop"))
    }

    @Test
    fun matchesSenderAlone() {
        assertTrue(ruleMatches("bank", emptyList(), "BANK", "Anything"))
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
        assertFalse(ruleMatches("", emptyList(), "BANK", "Purchase"))
    }
}
