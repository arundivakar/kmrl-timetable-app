package com.example.kmrltimetable.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.kmrltimetable.data.local.entity.DayDefaultEntity
import com.example.kmrltimetable.data.local.entity.ScheduleOverrideEntity
import com.example.kmrltimetable.data.local.entity.StationEntity
import com.example.kmrltimetable.data.local.entity.StopTimeEntity
import com.example.kmrltimetable.data.local.entity.SyncMetadataEntity
import com.example.kmrltimetable.data.local.entity.TimetableEntity
import com.example.kmrltimetable.data.local.entity.TripEntity
import java.io.File
import java.io.FileOutputStream

@Database(
    entities = [
        StationEntity::class,
        TimetableEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        ScheduleOverrideEntity::class,
        DayDefaultEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DB_NAME = "kmrl_timetable.db"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Ensure the pre-packaged DB is copied if it doesn't exist
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists()) {
                    copyDatabaseFromAssets(context)
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }

        private fun copyDatabaseFromAssets(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()
            
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
