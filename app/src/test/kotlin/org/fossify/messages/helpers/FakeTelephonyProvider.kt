package org.fossify.messages.helpers

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import org.robolectric.Robolectric

/**
 * A small stand-in for the phone's SMS and MMS store, so that code reading it can run under Robolectric. It has only
 * the columns this app's filter code uses, and keeps everything in memory.
 */
class FakeTelephonyProvider : ContentProvider() {
    lateinit var db: SQLiteDatabase
        private set

    override fun onCreate(): Boolean {
        db = SQLiteDatabase.create(null)
        db.execSQL(
            "CREATE TABLE sms (_id INTEGER PRIMARY KEY, thread_id INTEGER, address TEXT, body TEXT, date INTEGER, " +
                "type INTEGER, read INTEGER DEFAULT 0, seen INTEGER DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE pdu (_id INTEGER PRIMARY KEY, thread_id INTEGER, date INTEGER, msg_box INTEGER, " +
                "read INTEGER DEFAULT 0, seen INTEGER DEFAULT 0)"
        )
        db.execSQL("CREATE TABLE part (_id INTEGER PRIMARY KEY, mid INTEGER, ct TEXT, text TEXT)")
        db.execSQL("CREATE TABLE addr (_id INTEGER PRIMARY KEY, msg_id INTEGER, address TEXT, type INTEGER)")
        db.execSQL("CREATE TABLE threads (_id INTEGER PRIMARY KEY, archived INTEGER DEFAULT 0)")
        return true
    }

    /** Imitates phones whose message store cannot archive conversations. */
    fun removeArchive() {
        db.execSQL("DROP TABLE threads")
        db.execSQL("CREATE TABLE threads (_id INTEGER PRIMARY KEY)")
    }

    fun addSms(threadId: Long, address: String, body: String, dateMillis: Long): Long {
        val thread = ContentValues().apply { put("_id", threadId) }
        db.insertWithOnConflict("threads", null, thread, SQLiteDatabase.CONFLICT_IGNORE)
        return db.insert("sms", null, ContentValues().apply {
            put("thread_id", threadId)
            put("address", address)
            put("body", body)
            put("date", dateMillis)
            put("type", 1)
        })
    }

    fun isRead(smsId: Long) = query("sms", "read", smsId) == 1L

    fun isArchived(threadId: Long) = query("threads", "archived", threadId) == 1L

    private fun query(table: String, column: String, id: Long) =
        db.rawQuery("SELECT $column FROM $table WHERE _id = $id", null).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }

    // The table a uri names, and the row it names, if any.
    private fun target(uri: Uri): Pair<String, String?> {
        val segments = uri.pathSegments
        val id = segments.firstOrNull()?.toLongOrNull()
        return when (uri.authority) {
            "sms" -> "sms" to id?.let { "_id = $it" }
            "mms" -> when {
                segments.firstOrNull() == "part" -> "part" to null
                segments.getOrNull(1) == "addr" -> "addr" to "msg_id = $id"
                else -> "pdu" to id?.let { "_id = $it" }
            }

            // anything this fake does not know fails loudly instead of looking like an empty result
            else -> when (segments.firstOrNull()) {
                // only the archived flag is kept, so loading conversations from here finds nothing
                "conversations" -> "threads" to null
                else -> throw UnsupportedOperationException("FakeTelephonyProvider does not support $uri")
            }
        }
    }

    private fun where(row: String?, selection: String?) =
        listOfNotNull(row, selection?.takeIf { it.isNotBlank() }).joinToString(" AND ") { "($it)" }.ifEmpty { null }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val (table, row) = target(uri)
        val builder = SQLiteQueryBuilder().apply { tables = table }
        return builder.query(db, projection, where(row, selection), selectionArgs, null, null, sortOrder)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val (table, _) = target(uri)
        return ContentUris.withAppendedId(uri, db.insertOrThrow(table, null, values))
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val (table, row) = target(uri)
        return db.update(table, values, where(row, selection), selectionArgs)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val (table, row) = target(uri)
        return db.delete(table, where(row, selection), selectionArgs)
    }

    override fun getType(uri: Uri): String? = null

    companion object {
        /** Registers a fresh store for the SMS, MMS and conversation uris. */
        fun install(): FakeTelephonyProvider = Robolectric.buildContentProvider(FakeTelephonyProvider::class.java)
            .create(ProviderInfo().apply { authority = "sms;mms;mms-sms" })
            .get()
    }
}
