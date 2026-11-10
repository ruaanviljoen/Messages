package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class SpamFilterTest {
    private val keywords = DEFAULT_SPAM_KEYWORDS

    @Test
    fun matchesKeywordsAnywhereIgnoringCase() {
        assertEquals(listOf("opt out"), findSpamKeywords("Opt Out to stop", listOf("opt out")))
        assertEquals(listOf("opt out"), findSpamKeywords("Big sale! Reply OPT OUT to stop", listOf("opt out")))
        assertEquals(listOf("optout"), findSpamKeywords("Sale today.Optout", listOf("optout")))
    }

    @Test
    fun matchesStartingKeywords() {
        assertEquals(setOf("*135*181#"), findSpamKeywords("Dial *135*181# to stop", keywords).toSet())
        assertEquals(setOf("opt-out", "opt-out.co"), findSpamKeywords("Visit opt-out.co.za", keywords).toSet())
    }

    @Test
    fun ignoresMessagesWithoutKeywords() {
        assertEquals(emptyList<String>(), findSpamKeywords("Your OTP is 123456", keywords))
        assertEquals(emptyList<String>(), findSpamKeywords("Anything", listOf("")))
    }

    @Test
    fun comparesLocalAndInternationalNumbers() {
        assertEquals("0820000001".toComparableNumber(), "+27820000001".toComparableNumber())
        assertEquals("082 000 0001".toComparableNumber(), "+27 82 000 0001".toComparableNumber())
        assertEquals("", "Vodacom".toComparableNumber())
        assertEquals("mtn4u", "MTN4U".toSenderKey())
        assertEquals("820000001", "082 000 0001".toSenderKey())
        assertEquals("820000001", "+27 (82) 000-0001".toSenderKey())
        assertEquals("12345", "12345".toSenderKey())
    }
}
