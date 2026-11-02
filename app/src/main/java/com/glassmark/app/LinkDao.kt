package com.glassmark.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Insert suspend fun insert(link: LinkEntity)
    @Query("SELECT * FROM saved_links ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<LinkEntity>>
    @Query("DELETE FROM saved_links WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("UPDATE saved_links SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)
}
