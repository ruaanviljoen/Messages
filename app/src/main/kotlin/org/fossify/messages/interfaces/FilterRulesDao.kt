package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule

@Dao
interface FilterRulesDao {
    // the Spam rule first, then the others in order
    @Query("SELECT * FROM filter_rules ORDER BY is_spam DESC, position")
    fun getRules(): List<FilterRule>

    @Query("SELECT * FROM filter_rules WHERE is_spam = 1 LIMIT 1")
    fun getSpamRule(): FilterRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRule(rule: FilterRule): Long

    @Update
    fun updateRules(rules: List<FilterRule>)

    @Delete
    fun deleteRule(rule: FilterRule)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun getFolders(): List<FilterFolder>

    @Insert
    fun insertFolder(folder: FilterFolder): Long

    @Query("SELECT COUNT(*) FROM filter_rules WHERE folder_id = :folderId")
    fun countRules(folderId: Long): Int

    @Query("SELECT COUNT(*) FROM spam_messages WHERE folder_id = :folderId AND restored = 0")
    fun countMessages(folderId: Long): Int

    // Folders that no rule moves messages to and that hold no messages are no longer needed.
    @Query(
        "DELETE FROM folders WHERE id NOT IN (SELECT folder_id FROM filter_rules) " +
            "AND id NOT IN (SELECT folder_id FROM spam_messages WHERE restored = 0)"
    )
    fun deleteUnusedFolders()
}
