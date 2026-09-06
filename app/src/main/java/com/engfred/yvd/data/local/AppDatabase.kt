package com.engfred.yvd.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DownloadQueueEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadQueueDao(): DownloadQueueDao

    companion object {
        /**
         * v2: adds the MP3 download columns. Nullable so existing queue rows
         * keep working after the upgrade without a destructive wipe.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_queue ADD COLUMN audioContainer TEXT")
                db.execSQL("ALTER TABLE download_queue ADD COLUMN bitrateKbps INTEGER")
            }
        }
    }
}