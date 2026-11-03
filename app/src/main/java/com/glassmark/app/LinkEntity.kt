package com.glassmark.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** ownerUid = "" significa link local (modo visitante). Caso contrário é o cache da conta. */
@Entity(tableName = "saved_links")
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val collection: String = "Inbox",
    val savedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "") val docId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "") val ownerUid: String = "",
    @ColumnInfo(defaultValue = "") val ogImage: String = "",
    @ColumnInfo(defaultValue = "") val summary: String = "",
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "url" to url, "title" to title, "collection" to collection,
        "savedAt" to savedAt, "isFavorite" to isFavorite,
        "ogImage" to ogImage, "summary" to summary, "hidden" to hidden
    )
}

@Entity(tableName = "collections", primaryKeys = ["ownerUid", "name"])
data class CollectionEntity(
    val ownerUid: String,
    val name: String
)
