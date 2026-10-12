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
        private const val DB_VERSION_ASSET = "db_version.txt"
        private const val PREFS_NAME = "kmrl_database_prefs"
        private const val KEY_COPIED_SIGNATURE = "bundled_db_signature"

        private fun getAssetSignature(context: Context): String {
            return try {
                context.assets.open(DB_VERSION_ASSET).bufferedReader().use { it.readText().trim() }
            } catch (e: Exception) {
                try {
                    val fd = context.assets.openFd(DB_NAME)
                    "len_${fd.length}"
                } catch (ex: Exception) {
                    "v${BuildConfig.VERSION_CODE}"
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath(DB_NAME)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastSignature = prefs.getString(KEY_COPIED_SIGNATURE, "") ?: ""
                val currentSignature = getAssetSignature(context)

                // Copy pre-packaged DB if it doesn't exist or if asset signature has changed
                val needsCopy = !dbFile.exists() || lastSignature != currentSignature
                if (needsCopy) {
                    copyDatabaseFromAssets(context)
                    prefs.edit().putString(KEY_COPIED_SIGNATURE, currentSignature).apply()
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

        fun forceReloadFromAssets(context: Context) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    // Ignore
                }
                INSTANCE = null
                copyDatabaseFromAssets(context)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_COPIED_SIGNATURE, getAssetSignature(context)).apply()
            }
        }

        fun replaceDatabaseWithFile(context: Context, newFile: File) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    // Ignore
                }
                INSTANCE = null
                val dbFile = context.getDatabasePath(DB_NAME)
                dbFile.parentFile?.mkdirs()

                val walFile = File(dbFile.path + "-wal")
                if (walFile.exists()) walFile.delete()
                val shmFile = File(dbFile.path + "-shm")
                if (shmFile.exists()) shmFile.delete()

                newFile.copyTo(dbFile, overwrite = true)
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
