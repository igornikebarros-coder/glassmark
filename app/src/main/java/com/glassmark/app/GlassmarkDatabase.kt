package com.glassmark.app

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LinkEntity::class, CollectionEntity::class], version = 4, exportSchema = false)
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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_links ADD COLUMN ogImage TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE saved_links ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE saved_links ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_links ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE saved_links ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE saved_links ADD COLUMN remindAt INTEGER NOT NULL DEFAULT 0")
    }
}

/** Instância única do banco (app, widget, compartilhamento e lembretes). */
object AppDb {
    @Volatile private var inst: GlassmarkDatabase? = null
    fun get(ctx: android.content.Context): GlassmarkDatabase = inst ?: synchronized(this) {
        inst ?: androidx.room.Room.databaseBuilder(ctx.applicationContext, GlassmarkDatabase::class.java, "glassmark.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { inst = it }
    }
}

/** Normaliza URL para detectar duplicados. */
fun normalizeUrl(u: String): String = runCatching {
    val uri = android.net.Uri.parse(u.trim())
    val host = (uri.host ?: return@runCatching u.trim().lowercase()).lowercase().removePrefix("www.").removePrefix("m.")
    val q = uri.queryParameterNames.filterNot { it.startsWith("utm_") || it in setOf("fbclid", "gclid", "si", "igshid") }.sorted()
        .joinToString("&") { k -> "$k=${uri.getQueryParameter(k)}" }
    host + (uri.path ?: "").trimEnd('/') + (if (q.isNotEmpty()) "?$q" else "")
}.getOrDefault(u.trim().lowercase())
