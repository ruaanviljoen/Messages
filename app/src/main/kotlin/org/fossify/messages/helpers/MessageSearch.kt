package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.extensions.getMMSSender
import org.fossify.messages.extensions.getNameAndPhotoFromPhoneNumber
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.models.Message

/** Whether a message body contains the searched text, ignoring case. */
fun matchesSearch(body: String, text: String) = text.isNotEmpty() && body.contains(text, ignoreCase = true)

/**
 * The messages whose text contains [text], ignoring case, in every conversation and folder. It reads the phone's
 * message store, because the app's own cache holds only the latest messages of each conversation. Scheduled messages,
 * which live only in the cache, come from there. Run it off the main thread.
 */
fun Context.searchMessages(text: String): List<Message> {
    val found = ArrayList<Message>()
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.READ),
        showErrors = true
    ) { cursor ->
        val body = cursor.getStringValue(Sms.BODY).orEmpty()
        if (matchesSearch(body, text)) {
            found += searchResultMessage(
                id = cursor.getLongValue(Sms._ID),
                isMMS = false,
                threadId = cursor.getLongValue(Sms.THREAD_ID),
                address = cursor.getStringValue(Sms.ADDRESS).orEmpty(),
                body = body,
                date = (cursor.getLongValue(Sms.DATE) / MILLIS_PER_SECOND).toInt(),
                type = cursor.getIntValue(Sms.TYPE),
                read = cursor.getIntValue(Sms.READ) == 1
            )
        }
    }

    val mmsTexts = getMmsTexts().filterValues { matchesSearch(it, text) }
    if (mmsTexts.isNotEmpty()) {
        queryCursor(
            uri = Mms.CONTENT_URI,
            projection = arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.READ),
            selection = "${Mms._ID} IN (${mmsTexts.keys.joinToString(",")})",
            showErrors = true
        ) { cursor ->
            val id = cursor.getLongValue(Mms._ID)
            found += searchResultMessage(
                id = id,
                isMMS = true,
                threadId = cursor.getLongValue(Mms.THREAD_ID),
                address = getMMSSender(id),
                body = mmsTexts[id].orEmpty(),
                date = cursor.getIntValue(Mms.DATE),
                type = cursor.getIntValue(Mms.MESSAGE_BOX),
                read = cursor.getIntValue(Mms.READ) == 1
            )
        }
    }

    return found + messagesDB.getMessagesWithText("%$text%").filter { it.isScheduled }
}

@Suppress("LongParameterList")
private fun Context.searchResultMessage(
    id: Long,
    isMMS: Boolean,
    threadId: Long,
    address: String,
    body: String,
    date: Int,
    type: Int,
    read: Boolean
): Message {
    val namePhoto = getNameAndPhotoFromPhoneNumber(address)
    val participant = SimpleContact(
        rawId = 0,
        contactId = 0,
        name = namePhoto.name,
        photoUri = namePhoto.photoUri.orEmpty(),
        phoneNumbers = arrayListOf(PhoneNumber(address, 0, "", address)),
        birthdays = ArrayList(),
        anniversaries = ArrayList()
    )
    return Message(
        id = id,
        body = body,
        type = type,
        status = 0,
        participants = arrayListOf(participant),
        date = date,
        read = read,
        threadId = threadId,
        isMMS = isMMS,
        attachment = null,
        senderPhoneNumber = address,
        senderName = namePhoto.name,
        senderPhotoUri = namePhoto.photoUri.orEmpty(),
        subscriptionId = -1
    )
}
