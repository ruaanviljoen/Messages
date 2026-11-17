package org.fossify.messages.helpers

import org.fossify.messages.models.Conversation
import org.fossify.messages.models.FilteredMessage
import org.junit.Assert.assertEquals
import org.junit.Test

// Made-up names and numbers only.
class FolderSearchTest {
    private fun conversation(threadId: Long, title: String, number: String) = Conversation(
        threadId = threadId, snippet = "", date = 0, read = true, title = title, photoUri = "",
        isGroupConversation = false, phoneNumber = number
    )

    private fun entry(id: Long, threadId: Long, date: Int) = FilteredMessage(id, false, threadId, date, folderId = 5)

    private val courier = conversation(1, "COURIER", "COURIER")
    private val shop = conversation(2, "Shop", "0820000001")
    private val texts = mapOf(
        entry(10, 1, 100) to "Your parcel is on its way",
        entry(11, 1, 200) to "Your parcel was delivered",
        entry(12, 2, 150) to "Your order is ready"
    )

    @Test
    fun findsConversationsByMessageAndOpensTheNewestMatch() {
        val found = searchFolder(listOf(courier, shop), texts, "PARCEL")
        assertEquals(listOf(1L), found.map { it.first.threadId })
        assertEquals("Your parcel was delivered", found.single().first.snippet)
        assertEquals(11L, found.single().second)
    }

    @Test
    fun findsConversationsByNameOrNumber() {
        val byName = searchFolder(listOf(courier, shop), texts, "shop")
        assertEquals(listOf(2L to null), byName.map { it.first.threadId to it.second })
        assertEquals(listOf(2L), searchFolder(listOf(courier, shop), texts, "0820000001").map { it.first.threadId })
    }

    @Test
    fun findsNothingForOtherText() {
        assertEquals(emptyList<Long>(), searchFolder(listOf(courier, shop), texts, "invoice").map { it.first.threadId })
    }
}
