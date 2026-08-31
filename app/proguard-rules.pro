# Add project specific ProGuard rules here.
# Room
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase {
    <methods>;
}

# Keep Entities and DAOs
-keep class com.example.kmrltimetable.data.local.** { *; }
-keep class com.example.kmrltimetable.data.remote.** { *; }
-keep class com.example.kmrltimetable.domain.model.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep models for Serialization/Firebase
-keepclassmembers class com.example.kmrltimetable.** {
    <fields>;
    <init>(...);
}
-keep class com.example.kmrltimetable.data.** { *; }
