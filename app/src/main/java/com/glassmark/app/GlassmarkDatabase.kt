package com.glassmark.app

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LinkEntity::class, CollectionEntity::class], version = 2, exportSchema = false)
abstract class GlassmarkDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
    abstract fun collectionDao(): CollectionDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_links ADD COLUMN docId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE saved_links ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE saved_links SET docId = lower(hex(randomblob(16)))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`ownerUid` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`ownerUid`, `name`))")
    }
}
