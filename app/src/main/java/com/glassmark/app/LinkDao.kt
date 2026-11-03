package com.glassmark.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(link: LinkEntity): Long

    @Query("SELECT * FROM saved_links WHERE ownerUid = :uid ORDER BY savedAt DESC")
    fun observe(uid: String): Flow<List<LinkEntity>>

    @Query("SELECT * FROM saved_links WHERE ownerUid = :uid")
    suspend fun all(uid: String): List<LinkEntity>

    @Query("DELETE FROM saved_links WHERE ownerUid = :uid AND docId = :docId")
    suspend fun delete(uid: String, docId: String)

    @Query("DELETE FROM saved_links WHERE ownerUid = :uid")
    suspend fun clear(uid: String)

    @Query("UPDATE saved_links SET isFavorite = NOT isFavorite WHERE ownerUid = :uid AND docId = :docId")
    suspend fun toggleFavorite(uid: String, docId: String)

    @Query("UPDATE saved_links SET collection = :new WHERE ownerUid = :uid AND collection = :old")
    suspend fun moveCollection(uid: String, old: String, new: String)

    @Query("SELECT COUNT(*) FROM saved_links WHERE ownerUid = ''")
    suspend fun guestCount(): Int
}

@Dao
interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(c: CollectionEntity)

    @Query("SELECT * FROM collections WHERE ownerUid = :uid ORDER BY name")
    fun observe(uid: String): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE ownerUid = :uid")
    suspend fun all(uid: String): List<CollectionEntity>

    @Query("DELETE FROM collections WHERE ownerUid = :uid AND name = :name")
    suspend fun delete(uid: String, name: String)

    @Query("DELETE FROM collections WHERE ownerUid = :uid")
    suspend fun clear(uid: String)
}
