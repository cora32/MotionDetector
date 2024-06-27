package io.iskopasi.simplymotion.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MDDao {
    @Query("SELECT * FROM mdlog ORDER BY date DESC")
    fun getAll(): Flow<List<MDLog>>

    @Query("DELETE FROM mdlog")
    fun clearAll()

    @Insert
    suspend fun insert(mdLog: MDLog)
}