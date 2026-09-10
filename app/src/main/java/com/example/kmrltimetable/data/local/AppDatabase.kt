package com.example.kmrltimetable.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.kmrltimetable.BuildConfig
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
        private const val PREFS_NAME = "kmrl_database_prefs"
        private const val KEY_COPIED_VERSION = "bundled_db_version"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath(DB_NAME)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastVersion = prefs.getInt(KEY_COPIED_VERSION, -1)
                val currentVersion = BuildConfig.VERSION_CODE

                // Copy pre-packaged DB if it doesn't exist or if app was updated
                if (!dbFile.exists() || lastVersion < currentVersion) {
                    copyDatabaseFromAssets(context)
                    prefs.edit().putInt(KEY_COPIED_VERSION, currentVersion).apply()
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
