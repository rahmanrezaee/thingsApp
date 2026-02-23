package com.example.thingsappandroid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.thingsappandroid.data.local.dao.BatteryReadingDao
import com.example.thingsappandroid.data.local.dao.ConsumptionDao
import com.example.thingsappandroid.data.local.entity.BatteryReadingEntity
import com.example.thingsappandroid.data.local.entity.ConsumptionEntity

@Database(
    entities = [ConsumptionEntity::class, BatteryReadingEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun consumptionDao(): ConsumptionDao
    abstract fun batteryReadingDao(): BatteryReadingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Migration v2 -> v3: add upload status columns (we keep history; no deletes on upload). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // battery_readings
                db.execSQL("ALTER TABLE battery_readings ADD COLUMN uploadStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE battery_readings ADD COLUMN uploadedAt INTEGER")
                db.execSQL("ALTER TABLE battery_readings ADD COLUMN lastUploadError TEXT")

                // pending_consumption
                db.execSQL("ALTER TABLE pending_consumption ADD COLUMN uploadStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE pending_consumption ADD COLUMN uploadedAt INTEGER")
                db.execSQL("ALTER TABLE pending_consumption ADD COLUMN lastUploadError TEXT")
                db.execSQL("ALTER TABLE pending_consumption ADD COLUMN measurementId TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "things_app_database"
            )
                .addMigrations(MIGRATION_2_3)
                .build()
        }
    }
}
