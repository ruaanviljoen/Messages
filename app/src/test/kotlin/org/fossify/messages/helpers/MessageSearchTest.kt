package org.fossify.messages.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSearchTest {
    @Test
    fun findsWordAtStartMiddleAndEnd() {
        assertTrue(matchesSearch("Purchase of R250 approved", "purchase"))
        assertTrue(matchesSearch("Thank you for your purchase at the store", "purchase"))
        assertTrue(matchesSearch("Card used for a purchase", "purchase"))
    }

    @Test
    fun ignoresCase() {
        assertTrue(matchesSearch("PURCHASE APPROVED", "purchase"))
        assertTrue(matchesSearch("purchase approved", "PurChase"))
        assertTrue(matchesSearch("Café Ñandú", "ñandú"))
    }

    @Test
    fun doesNotMatchOtherText() {
        assertFalse(matchesSearch("Your OTP is 123456", "purchase"))
        assertFalse(matchesSearch("anything", ""))
    }
}
