# Implementation Plan: Quicken LifeHub & Pixel Smart Dashboard

## 1. User Intent & Clarifications

### Goal
Evolve Mnemosyne into an intelligent, frictionless **Life Hub** that merges:
1. **Quicken LifeHub Architecture:** Purpose-driven Smart Life Folders (Personal, Health, Financial/Legal, Home & Work) that transform notes and documents into organized life areas.
2. **Google Pixel Minimalist "At a Glance" Widget:** Sleek, low-text live smart chip displaying current context (next calendar event, step goal progress, weather/greeting, active reminder).
3. **Microsoft Launcher-Style Device Telemetry (Pixel Aesthetic):** Minimalist metrics for daily step count, screen time, and recently accessed files without text overload.
4. **Calendar & Local Reminders:** Seamless sync with Android `CalendarContract` plus an offline-first local reminders fallback.
5. **TagSpaces Smart Vault:** Dedicated file explorer tab with tag pills, format badges, and instant file export.
6. **Subtle Fade Transitions & Storypad Capture:** Effortless navigation and instant multi-type capture (voice, photo, text).

---

## 2. Architecture & Design Decisions

### A. Main Dashboard Layout (Quicken LifeHub + Pixel "At a Glance")
The landing screen features a clean, high-trust hierarchy:
- **Pixel "At a Glance" Smart Header:**
  - Date & contextual greeting with live dynamic status.
  - Next upcoming event or reminder pill (tap to view/complete).
  - Minimalist progress chip: Steps ring & screen time metric in a single clean row.
- **Quicken LifeHub Category Grid:**
  - 4 purposeful smart life tiles:
    1. *Personal & Wellbeing* (Thoughts, mood, reflections)
    2. *Health & Activity* (Steps, exercise, doctor notes, vitals)
    3. *Finance & Documents* (Contracts, bills, receipts, IDs)
    4. *Projects & Home* (Tasks, property, gear, family)
  - Each tile displays item count and latest activity badge.
- **Recent Files & Media Carousel (Gallery Go style):**
  - Instant access to recent photos, audio memos, and documents.
- **Storypad Quick Capture FAB:**
  - 1-tap capture for default note type; long-press for multi-format selector.

### B. Device Telemetry & Health Integration (Real Android APIs)
- **Step Counting:** Android `SensorManager` with `Sensor.TYPE_STEP_COUNTER` and `ACTIVITY_RECOGNITION` support.
- **Screen Time & Usage:** `UsageStatsManager` query for today's foreground screen time and top 3 apps, with an in-app button to grant usage access if needed.
- **Calendar & Reminders:** Query `CalendarContract.Instances` for upcoming schedule. If permissions are not granted or for offline-first privacy, automatically fallback to internal Room `LocalReminder` records.

### C. Visual Aesthetics & Motion
- **Style:** Clean Google Pixel minimalism meets Quicken LifeHub structure.
- **Typography & Layout:** Minimal text, high-legibility bold titles, concise pills and badges, avoiding dense walls of text.
- **Motion:** Calm, low-latency cross-fade animations (`fadeIn(tween(180))` + `fadeOut(tween(140))`).

---

## 3. File-by-File Implementation Plan

### 1. `app/src/main/AndroidManifest.xml`
- Add permissions:
  - `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />`
  - `<uses-permission android:name="android.permission.READ_CALENDAR" />`
  - `<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />`

### 2. `app/src/main/java/com/example/data/model/LocalReminder.kt` & `LocalReminderDao.kt`
- Room entity for local offline tasks and reminders (title, dueTimestamp, isCompleted, priority, category).
- Room DAO for querying active reminders, marking done, and inserting new reminders.

### 3. `app/src/main/java/com/example/telemetry/DeviceLifeHubManager.kt` (New Manager)
- Helper class providing reactive StateFlows:
  - `todaySteps: StateFlow<Int>` via sensor listener.
  - `todayScreenTimeMinutes: StateFlow<Int>` via `UsageStatsManager`.
  - `upcomingEvents: StateFlow<List<CalendarEventItem>>` querying Android `CalendarContract`.
  - Graceful fallbacks when permissions are denied or sensors are absent in emulators.

### 4. `app/src/main/java/com/example/ui/components/PixelAtAGlanceCard.kt` (New Component)
- Pixel-inspired compact card:
  - Left: Date, temperature/greeting, and next calendar event or reminder pill.
  - Right: Minimalist steps chip and screen time indicator.
  - Tap event to open details or toggle completion.

### 5. `app/src/main/java/com/example/ui/components/LifeHubCategoryGrid.kt` (New Component)
- 4 Quicken LifeHub smart category cards:
  - Color-coded icons, title, total item count, and quick tag filter link.
  - Tapping a category filters notes/vault into that life domain.

### 6. `app/src/main/java/com/example/ui/screens/VaultExplorerScreen.kt` (New Screen)
- TagSpaces-style smart file explorer:
  - Format chips: `All`, `Notes (.md)`, `Audio (.m4a)`, `Photos (.jpg)`.
  - Tag filter pills carousel.
  - Responsive 2-column card grid with format badges, file size indicator, and 1-tap share/export.

### 7. `app/src/main/java/com/example/ui/screens/TimelineScreen.kt`
- Integrate `PixelAtAGlanceCard` at the top of the Life Hub.
- Integrate `LifeHubCategoryGrid`.
- Retain the recent visual memory album and chronological notes stream.
- Polish layout to remove wordy descriptions and replace with clean, scannable cards.

### 8. `app/src/main/java/com/example/viewmodel/JournalViewModel.kt`
- Integrate `DeviceLifeHubManager` telemetry flows.
- Add local reminders CRUD operations.
- Add category filtering and Vault file mapping.

### 9. `app/src/main/java/com/example/MainActivity.kt`
- Add `MainNavTab.VAULT` with `Icons.Default.FolderOpen`.
- Configure `VaultExplorerScreen` in the navigation stack with smooth fade transitions.

### 10. `app/src/main/res/values/strings.xml` & `values-es/strings.xml`
- Add localized strings for LifeHub categories, Pixel widget states, reminders, and vault filters.

---

## 4. Verification & Testing Checklist

- [ ] **Build Verification:** `compile_applet` succeeds without errors.
- [ ] **Unit Tests:** `gradle :app:testDebugUnitTest` passes.
- [ ] **At a Glance Display:** Next event / reminder renders cleanly; step count and screen time render without overflow.
- [ ] **Category Filtering:** Tapping LifeHub categories filters notes and files accurately.
- [ ] **Vault Explorer:** TagSpaces tag filters and format pills dynamically filter the vault grid.
- [ ] **Offline Resilience:** Calendar gracefully falls back to local reminders if calendar permissions are not granted.
- [ ] **Visual Polish:** Pixel minimalist aesthetic, scannable chips, zero walls of text, and subtle fade transitions.
