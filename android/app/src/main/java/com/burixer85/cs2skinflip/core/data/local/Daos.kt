package com.burixer85.cs2skinflip.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE skinId = :skinId LIMIT 1")
    suspend fun findBySkinId(skinId: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchlistEntity): Long

    @Update
    suspend fun update(item: WatchlistEntity)

    @Delete
    suspend fun delete(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun deleteById(id: Long)
}

