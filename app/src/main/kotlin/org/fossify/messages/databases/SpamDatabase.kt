package org.fossify.messages.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.messages.interfaces.SpamDao
import org.fossify.messages.interfaces.SpamThreadsDao
import org.fossify.messages.models.SpamMessage
import org.fossify.messages.models.SpamThread

/**
 * The spam filter's own database. It is kept apart from MessagesDatabase, which falls back to destructive migration,
 * so that merging upstream schema changes can never wipe the Spam folder.
 */
@Database(entities = [SpamMessage::class, SpamThread::class], version = 2)
abstract class SpamDatabase : RoomDatabase() {

    abstract fun SpamDao(): SpamDao

    abstract fun SpamThreadsDao(): SpamThreadsDao

    companion object {
        @Volatile
        private var db: SpamDatabase? = null

        fun getInstance(context: Context): SpamDatabase {
            return db ?: synchronized(SpamDatabase::class) {
                db ?: Room.databaseBuilder(context.applicationContext, SpamDatabase::class.java, "spam.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { db = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spam_threads` (`thread_id` INTEGER NOT NULL, " +
                        "`conversation_date` INTEGER NOT NULL, `snippet` TEXT, `snippet_date` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`thread_id`))"
                )
            }
        }
    }
}
