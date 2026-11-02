package com.glassmark.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_links")
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val collection: String = "Inbox",
    val savedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
