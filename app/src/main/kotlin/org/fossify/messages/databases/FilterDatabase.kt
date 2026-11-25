@file:Suppress("MagicNumber")
package org.fossify.messages.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.messages.interfaces.FilterRulesDao
import org.fossify.messages.interfaces.FilteredMessagesDao
import org.fossify.messages.interfaces.SpamThreadsDao
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.FilteredMessage
import org.fossify.messages.models.SpamThread

/**
 * The filters' own database: folders, rules and which messages they moved. It is kept apart from MessagesDatabase,
 * which falls back to destructive migration, so that merging upstream schema changes can never wipe the folders.
 */
@Database(
    entities = [FilteredMessage::class, SpamThread::class, FilterFolder::class, FilterRule::class],
    version = 6
)
abstract class FilterDatabase : RoomDatabase() {

    abstract fun FilteredMessagesDao(): FilteredMessagesDao

    abstract fun SpamThreadsDao(): SpamThreadsDao

    abstract fun FilterRulesDao(): FilterRulesDao

    companion object {
        @Volatile
        private var db: FilterDatabase? = null

        fun getInstance(context: Context): FilterDatabase {
            return db ?: synchronized(FilterDatabase::class) {
                db ?: Room.databaseBuilder(context.applicationContext, FilterDatabase::class.java, "spam.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `spam_messages` ADD COLUMN `folder_id` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `filter_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `folder_id` INTEGER NOT NULL, `sender` TEXT NOT NULL, " +
                        "`keywords` TEXT NOT NULL, `notify` INTEGER NOT NULL)"
                )
            }
        }

        // Spam becomes a stored rule; it is created from the Spam settings the first time it is needed.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `filter_rules` ADD COLUMN `action` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `filter_rules` ADD COLUMN `is_spam` INTEGER NOT NULL DEFAULT 0")
                // existing rules applied to everyone, so they keep applying to saved contacts
                db.execSQL("ALTER TABLE `filter_rules` ADD COLUMN `apply_to_contacts` INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Rules can delete the messages they file after some days; existing rules keep them.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `filter_rules` ADD COLUMN `delete_after_days` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Every rule can have numbers it never applies to. The Spam rule takes over the old Spam setting in code, in
        // getSpamRule, since that setting is not in this database.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `filter_rules` ADD COLUMN `exempt_senders` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
