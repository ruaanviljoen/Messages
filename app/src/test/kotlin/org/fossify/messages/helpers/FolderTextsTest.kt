package org.fossify.messages.helpers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.fossify.messages.models.FilteredMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Made-up senders only.
@RunWith(RobolectricTestRunner::class)
class FolderTextsTest {
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
    fun readsTheFolderTextsAndSkipsReusedIds() = background {
        val kept = store.addSms(1, "COURIER", "Your parcel is on its way", 1_000_000)
        val reused = store.addSms(2, "COURIER", "A newer message with a reused id", 2_000_000)
        val other = store.addSms(3, "SHOP", "Not in this folder", 1_000_000)
        context.filteredDB.insert(FilteredMessage(kept, false, 1, 1_000, folderId = 5))
        // the entry was made for an older message that had this id
        context.filteredDB.insert(FilteredMessage(reused, false, 2, 500, folderId = 5))
        context.filteredDB.insert(FilteredMessage(other, false, 3, 1_000, folderId = 6))

        val texts = context.getFolderTexts(5)
        assertEquals(mapOf(kept to "Your parcel is on its way"), texts.mapKeys { it.key.id })
    }
}
