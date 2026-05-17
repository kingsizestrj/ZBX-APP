package com.zbxapp.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemDao {

    @Query("SELECT * FROM problems ORDER BY clock DESC")
    fun observeAll(): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM problems WHERE eventid = :eventid LIMIT 1")
    suspend fun getById(eventid: String): ProblemEntity?

    @Query("DELETE FROM problems")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProblemEntity>)

    /** Atomic refresh: clear and replace, so stale rows do not linger. */
    @Transaction
    suspend fun replaceAll(items: List<ProblemEntity>) {
        deleteAll()
        insertAll(items)
    }
}
