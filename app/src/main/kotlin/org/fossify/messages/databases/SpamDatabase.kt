package org.fossify.messages.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.fossify.messages.interfaces.SpamDao
import org.fossify.messages.models.SpamMessage

/**
 * The spam filter's own database. It is kept apart from MessagesDatabase, which falls back to destructive migration,
 * so that merging upstream schema changes can never wipe the Spam folder.
 */
@Database(entities = [SpamMessage::class], version = 1)
abstract class SpamDatabase : RoomDatabase() {

    abstract fun SpamDao(): SpamDao

    companion object {
        @Volatile
        private var db: SpamDatabase? = null

        fun getInstance(context: Context): SpamDatabase {
            return db ?: synchronized(SpamDatabase::class) {
                db ?: Room.databaseBuilder(context.applicationContext, SpamDatabase::class.java, "spam.db")
                    .build()
                    .also { db = it }
            }
        }
    }
}
