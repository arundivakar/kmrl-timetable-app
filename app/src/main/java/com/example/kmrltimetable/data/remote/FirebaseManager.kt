package com.example.kmrltimetable.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Centralized manager for all Firebase Realtime Database operations.
 *
 * DATABASE STRUCTURE:
 * /config/version          – incremented when any assignment changes
 * /config/updated_at       – ISO timestamp of last change
 * /date_assignments/{YYYY-MM-DD}  – timetable name for a specific date
 * /day_defaults/{0-6}      – default timetable per day-of-week (0=Mon)
 */
object FirebaseManager {

    // Explicitly specify the URL because the RTDB is in Asia-Southeast1 and
    // google-services.json might not have it if downloaded before DB creation.
    private const val DB_URL = "https://kmrl-train-finder-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val db by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // -----------------------------------------------------------------------
    // Auth helpers
    // -----------------------------------------------------------------------

    /** Sign in the admin with email + password. Throws on failure. */
    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun signOut() = auth.signOut()

    fun currentUid(): String? = auth.currentUser?.uid

    fun isSignedIn(): Boolean = auth.currentUser != null

    // -----------------------------------------------------------------------
    // Config (version check)
    // -----------------------------------------------------------------------

    data class RemoteConfig(
        val version: Long = 0,
        val updatedAt: String = ""
    )

    /** Fetch the lightweight config node to compare versions. */
    suspend fun fetchConfig(): RemoteConfig {
        val snap = db.reference.child("config").singleValueEvent()
        return RemoteConfig(
            version    = snap.child("version").getValue(Long::class.java) ?: 0L,
            updatedAt  = snap.child("updated_at").getValue(String::class.java) ?: ""
        )
    }

    // -----------------------------------------------------------------------
    // Date assignments
    // -----------------------------------------------------------------------

    /** Returns map of { "YYYY-MM-DD" -> timetableName } */
    suspend fun fetchDateAssignments(): Map<String, String> {
        val snap = db.reference.child("date_assignments").singleValueEvent()
        val result = mutableMapOf<String, String>()
        snap.children.forEach { child ->
            val key   = child.key ?: return@forEach
            val value = child.getValue(String::class.java) ?: return@forEach
            result[key] = value
        }
        return result
    }

    /**
     * Admin: assign a timetable to a date.
     * Also bumps config version so all clients know to re-sync.
     */
    suspend fun setDateAssignment(date: String, timetableName: String, adminEmail: String) {
        db.reference.child("date_assignments").child(date).setValue(timetableName).await()
        bumpConfigVersion(adminEmail)
    }

    /** Admin: remove a specific date override (reverts to day-of-week default). */
    suspend fun removeDateAssignment(date: String, adminEmail: String) {
        db.reference.child("date_assignments").child(date).removeValue().await()
        bumpConfigVersion(adminEmail)
    }

    // -----------------------------------------------------------------------
    // Day defaults
    // -----------------------------------------------------------------------

    /** Returns map of { dayOfWeek(Int) -> timetableName } */
    suspend fun fetchDayDefaults(): Map<Int, String> {
        val snap = db.reference.child("day_defaults").singleValueEvent()
        val result = mutableMapOf<Int, String>()
        snap.children.forEach { child ->
            val key   = child.key?.toIntOrNull() ?: return@forEach
            val value = child.getValue(String::class.java) ?: return@forEach
            result[key] = value
        }
        return result
    }

    // -----------------------------------------------------------------------
    // Timetable registry (list of available timetables)
    // -----------------------------------------------------------------------

    data class RemoteTimetableInfo(
        val name: String = "",
        val description: String = "",
        val uploadedAt: String = "",
        val status: String = "draft"     // "draft" | "published"
    )

    /** Returns the list of all registered timetables in Firebase. */
    suspend fun fetchTimetableRegistry(): List<RemoteTimetableInfo> {
        val snap = db.reference.child("timetable_registry").singleValueEvent()
        val result = mutableListOf<RemoteTimetableInfo>()
        snap.children.forEach { child ->
            val name        = child.key ?: return@forEach
            val description = child.child("description").getValue(String::class.java) ?: ""
            val uploadedAt  = child.child("uploaded_at").getValue(String::class.java) ?: ""
            val status      = child.child("status").getValue(String::class.java) ?: "draft"
            result.add(RemoteTimetableInfo(name, description, uploadedAt, status))
        }
        return result
    }

    // -----------------------------------------------------------------------
    // Audit log
    // -----------------------------------------------------------------------

    /** Write an audit entry. Called internally whenever an admin makes a change. */
    suspend fun writeAuditLog(adminEmail: String, action: String, details: String) {
        val entry = mapOf(
            "admin"     to adminEmail,
            "action"    to action,
            "details"   to details,
            "timestamp" to System.currentTimeMillis()
        )
        db.reference.child("audit_log").push().setValue(entry).await()
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private suspend fun bumpConfigVersion(adminEmail: String) {
        val configRef = db.reference.child("config")
        val current = fetchConfig()
        configRef.setValue(mapOf(
            "version"    to (current.version + 1),
            "updated_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                              .format(java.util.Date())
        )).await()
        writeAuditLog(adminEmail, "CONFIG_UPDATE", "Version bumped to ${current.version + 1}")
    }

    /** Coroutine-friendly single read from Firebase. */
    private suspend fun com.google.firebase.database.DatabaseReference.singleValueEvent(): DataSnapshot =
        suspendCancellableCoroutine { cont ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot)
                }
                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resumeWithException(error.toException())
                }
            }
            addListenerForSingleValueEvent(listener)
            cont.invokeOnCancellation { removeEventListener(listener) }
        }
}
