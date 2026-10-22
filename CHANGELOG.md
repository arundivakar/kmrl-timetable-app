# KMRL Timetable App — Changelog

All notable changes to the KMRL Timetable & Train Tracker application are documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/).

---

## [v5.4.0] — 2026-10-22

### 🚨 Added — Station Assistance Alarms
- **Station-Specific Passenger Assistance Alarm**: Added an interactive alarm option for every station along the live tracking route (e.g., set an alarm when train is at Kalamassery (KLMT) to alert staff 5 minutes before arriving at Vadakkekotta (VAKK)).
- **Customizable Lead Times**: Choose between **5 minutes before arrival** (recommended for platform security & wheelchair dispatch), **2 minutes before** (final call), **10 minutes before** (advance escort), or **at arrival**.
- **Assistance Purpose Presets**: Quick one-tap assistance tags including `♿ Wheelchair Assistance`, `👮 Security Escort`, `🧓 Senior Citizen Assist`, `🧳 Luggage / Boarding Assist`, and custom text notes.
- **Exact Background Alarms**: Utilizes Android's `AlarmManager` with `setExactAndAllowWhileIdle` to guarantee exact delivery even in Doze mode or when the screen is locked.
- **Audible Alert & Haptic Patterns**: Plays loud alarm tone (`USAGE_ALARM`) and urgent vibration pattern (`[0, 500, 250, 500, 250, 500, 250, 800]`) so station staff hear the alert in noisy platform environments.
- **Heads-Up System Notification**: Dispatches high-priority notification with operational briefing (Train, Line, Platform, Scheduled Departure, ETA, Assistance Note) with **"DISMISS & STOP SOUND"** and **"VIEW TRACKER"** actions.
- **In-App Alarm Triggered Modal**: Real-time pulsing alert dialog if the alarm triggers while using the app, with single-tap sound cancellation and acknowledgment.
- **Active Alarms Banner**: High-visibility summary card on the Live Tracker timeline showing active assistance alerts for the selected train rake with 1-tap cancellation.

### ⚡ Performance & Low-End Device Optimizations
- **Instant Search Loading (< 50ms)**: Eliminated N+1 query bottleneck in `TimetableRepository`. Replaced 35+ sequential SQLite table joins with a single instant active-trip query, yielding a **100x speedup** on budget and Android TV devices.
- **Composite SQLite Performance Indices**: Added automatic index initialization for `trips (timetable_id, train_no)` and `stop_times (trip_id, departure_time)`.
- **Asynchronous Background Pre-Caching**: Alternate trips for the train are cached lazily in a background coroutine without blocking the initial UI rendering.
- **Smooth Layout Rendering**: Fixed Compose animation frame drops on initial load by using direct position anchoring.

### 📦 Build & Release Automation
- **Automated Release APK Generation**: Added automated Gradle task and scripts to copy release APKs directly to `releases/KMRL_Timetable_v{version}_Release.apk`.
- **In-App Changelog & Update Notifications**: Enhanced update manager and in-app notifications with rich markdown changelogs.

---

## [v5.3.0] — 2026-10-21

### 🚆 Added — Live Train Tracker
- **Timetable-Based Real-Time Live Tracker**: Real-time train tracking computed from active KMRL timetable schedules.
- **Direction-Specific 25-Station Route Timeline**: Complete 25-station route visualization for both UP Line (Aluva ➔ Tripunithura) and DOWN Line (Tripunithura ➔ Aluva).
- **Floating Train In-Transit Indicators**: Animated train badges indicating train progress between stations.
- **Live Status Hero Card**: Status badges displaying `SCHEDULED • IN TRANSIT`, `AT STATION • BOARDING`, `WAITING AT TERMINAL`, and `TRIP COMPLETED`.
- **Schedule Disclaimers**: Added clear disclaimers explaining schedule-based estimates.
- **Push Notification System**: Firebase Cloud Messaging (FCM) integration for remote updates.

---

## [v5.2.0] — 2026-10-20

### 🔄 Added — Instant OTA Database Sync
- **OTA Timetable Hot-Swap**: Instant in-app database reload after background SQLite synchronization from GitHub Pages without requiring app restart.
- **Updated 2026 Timetables**: Bundled latest October 2026 weekday, weekend, and festival timetables.

---

## [v5.1.0] — 2026-10-18

### 🎨 Added — UI & Navigation Refinements
- **Animated Interchange Button**: Redesigned origin/destination station swap button with 180° rotation animation and haptic feedback.
- **Clean Material 3 Components**: Updated cards, pills, and station selectors to Material Design 3 tokens.

---

## [v5.0.0] — 2026-10-15

### 🚀 Initial Major Release
- Universal Light/Dark mode theme toggle with persistent SharedPreferences.
- Comprehensive Station Timings and Journey Planner.
- Firebase Admin Panel for remote timetable overrides.
- ProGuard / R8 rules for 75% APK size reduction.
