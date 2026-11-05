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

    @Query("UPDATE saved_links SET collection = :newName WHERE ownerUid = :uid AND collection = :oldName")
    suspend fun moveCollection(uid: String, oldName: String, newName: String)

    @Query("UPDATE saved_links SET ogImage = :v WHERE ownerUid = :uid AND docId = :docId")
    suspend fun setOg(uid: String, docId: String, v: String)

    @Query("UPDATE saved_links SET summary = :v WHERE ownerUid = :uid AND docId = :docId")
    suspend fun setSummary(uid: String, docId: String, v: String)

    @Query("UPDATE saved_links SET hidden = :v WHERE ownerUid = :uid AND docId = :docId")
    suspend fun setHidden(uid: String, docId: String, v: Boolean)

    @Query("UPDATE saved_links SET isRead = :v WHERE ownerUid = :uid AND docId = :docId")
    suspend fun setRead(uid: String, docId: String, v: Boolean)

    @Query("UPDATE saved_links SET remindAt = :v WHERE ownerUid = :uid AND docId = :docId")
    suspend fun setRemind(uid: String, docId: String, v: Long)

    @Query("UPDATE saved_links SET title = :v WHERE ownerUid = :uid AND docId = :docId")
    suspend fun setTitle(uid: String, docId: String, v: String)

    @Query("UPDATE saved_links SET collection = :v WHERE ownerUid = :uid AND docId = :docId")
    suspend fun setCollection(uid: String, docId: String, v: String)

    @Query("SELECT * FROM saved_links WHERE docId = :docId LIMIT 1")
    suspend fun byDocId(docId: String): LinkEntity?

    @Query("SELECT * FROM saved_links WHERE ownerUid = :uid AND hidden = 0 ORDER BY savedAt DESC LIMIT :n")
    suspend fun recent(uid: String, n: Int): List<LinkEntity>

    @Query("SELECT * FROM saved_links WHERE ownerUid = :uid AND hidden = 0 AND isFavorite = 1 ORDER BY savedAt DESC LIMIT :n")
    suspend fun favorites(uid: String, n: Int): List<LinkEntity>

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
