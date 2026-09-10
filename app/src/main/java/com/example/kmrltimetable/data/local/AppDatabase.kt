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
        private const val LATEST_REQUIRED_TIMETABLE = "16W070926_TPHTOFFPEAK_MRP1"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath(DB_NAME)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastVersion = prefs.getInt(KEY_COPIED_VERSION, -1)
                val currentVersion = BuildConfig.VERSION_CODE

                // Copy pre-packaged DB if it doesn't exist, if app was updated, or if missing latest schedule
                val needsCopy = !dbFile.exists() || lastVersion < currentVersion || isDatabaseMissingLatest(context)
                if (needsCopy) {
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

        private fun isDatabaseMissingLatest(context: Context): Boolean {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return true
            return try {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    db.rawQuery(
                        "SELECT 1 FROM timetables WHERE name = ? LIMIT 1",
                        arrayOf(LATEST_REQUIRED_TIMETABLE)
                    ).use { cursor ->
                        !cursor.moveToFirst()
                    }
                }
            } catch (e: Exception) {
                true // On corruption or schema failure, force copy
            }
        }

        private fun copyDatabaseFromAssets(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()

            // Remove existing WAL and SHM journal files to prevent corruption
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) walFile.delete()
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) shmFile.delete()

            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
