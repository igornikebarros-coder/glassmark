package com.glassmark.app

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LinkEntity::class], version = 1, exportSchema = false)
abstract class GlassmarkDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
}
