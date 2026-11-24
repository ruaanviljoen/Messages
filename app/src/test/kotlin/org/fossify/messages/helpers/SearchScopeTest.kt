package org.fossify.messages.helpers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.models.FilteredMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Made-up senders only.
@RunWith(RobolectricTestRunner::class)
class SearchScopeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var store: FakeTelephonyProvider

    @Before
    fun setUp() {
        store = FakeTelephonyProvider.install()
    }

    @After
    fun tearDown() {
        resetDatabases()
    }

    @Test
    fun aFolderSearchFindsOnlyThatFoldersMessagesAndConversations() = background {
        val inFolder = store.addSms(1, "COURIER", "Your parcel is on its way", 1_000_000)
        val inInbox = store.addSms(2, "SHOP", "Your parcel order is ready", 1_000_000)
        val inOtherFolder = store.addSms(3, "PROMO", "Win a parcel", 1_000_000)
        context.filteredDB.insert(FilteredMessage(inFolder, false, 1, 1_000, folderId = 5))
        context.filteredDB.insert(FilteredMessage(inOtherFolder, false, 3, 1_000, folderId = 6))
        context.cacheConversation(1, title = "Parcel courier")
        context.cacheConversation(2, title = "Parcel shop")

        val everything = context.findForSearch("parcel", folderId = null)
        assertEquals(setOf(inFolder, inInbox, inOtherFolder), everything.messages.map { it.id }.toSet())
        assertEquals(5L, everything.folders[inFolder to false])

        val folder = context.findForSearch("parcel", folderId = 5)
        assertEquals(listOf(inFolder), folder.messages.map { it.id })
        assertEquals(listOf(1L), folder.conversations.map { it.threadId })
        assertEquals("a folder search has no folder labels", emptyMap<Long, String>(), folder.folderNames)
    }
}
