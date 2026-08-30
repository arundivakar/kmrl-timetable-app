package com.example.kmrltimetable

import android.app.Application
import com.example.kmrltimetable.data.local.AppDatabase
import com.example.kmrltimetable.data.repository.TimetableRepository

class KMRLApplication : Application() {
    
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { TimetableRepository(database.timetableDao()) }
}
