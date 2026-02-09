package com.example.thingsappandroid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.thingsappandroid.data.local.dao.BatteryReadingDao
import com.example.thingsappandroid.data.local.dao.ConsumptionDao
import com.example.thingsappandroid.data.local.entity.BatteryReadingEntity
import com.example.thingsappandroid.data.local.entity.ConsumptionEntity

@Database(
    entities = [ConsumptionEntity::class, BatteryReadingEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun consumptionDao(): ConsumptionDao
    abstract fun batteryReadingDao(): BatteryReadingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
            ).fallbackToDestructiveMigration()
                .build()
        }
    }
}
