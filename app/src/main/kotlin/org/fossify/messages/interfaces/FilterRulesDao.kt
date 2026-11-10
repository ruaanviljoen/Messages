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
    @Query("SELECT * FROM filter_rules ORDER BY position")
    fun getRules(): List<FilterRule>

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
}
