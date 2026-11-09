package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.messages.models.SpamThread

@Dao
interface SpamThreadsDao {
    @Query("SELECT * FROM spam_threads")
    fun getAll(): List<SpamThread>

    @Query("DELETE FROM spam_threads")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(threads: List<SpamThread>)

    @Transaction
    fun replaceAll(threads: List<SpamThread>) {
        deleteAll()
        insertAll(threads)
    }
}
