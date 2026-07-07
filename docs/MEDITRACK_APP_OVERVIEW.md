# MediTrack App Overview

Last updated: 2026-07-07

## Purpose

MediTrack is a local, offline-first Android application for medication management. Its primary purpose is to help users:

- Add medications and dosing instructions.
- Define medication schedules using a simple prescription-style pattern such as `1+0+1`, or advanced interval rules.
- See the doses due today.
- Mark doses as taken or skipped.
- Automatically deduct stock only when a dose is taken.
- Track current stock and estimated days remaining.
- Warn when medication is low or out of stock.
- Track finite medication courses, auto-calculate the end date from course length, and warn when the entered stock is not enough.
- Receive local dose reminder notifications with Taken and Skip actions.

The app is intentionally local-only. It does not use login, backend servers, cloud sync, Firebase Cloud Messaging, ads, or paid SDKs.

## Current State

The app is a working Android MVP built with:

- Kotlin
- Jetpack Compose
- Room
- WorkManager
- ViewModel
- StateFlow
- Navigation Compose
- Android local notifications
- SharedPreferences for simple local settings
- GitHub Actions for debug APK builds
- Material 3 light, dark, and system theme modes

The app currently builds successfully as a debug APK and has JVM unit tests for core medication business logic.

The Add/Edit Medication flow is currently optimized for common prescription language. A typical entry can be made as:

```text
Paracetamol
Fixed Course
7 Days
Pattern: 1+0+1
Unit: tablet
Current stock: 10
```

This means 1 tablet in the morning, 0 in the afternoon, and 1 at night for 7 days. MediTrack auto-calculates the end date and the full course stock requirement.

Local debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions workflow:

```text
.github/workflows/android-debug-apk.yml
```

GitHub Actions artifact name:

```text
meditrack-debug-apk
```

## High-Level Architecture

The code is organized into practical layers:

```text
app/src/main/java/com/meditrack/
  data/
    local/          Room database, DAOs, entities, converters
    repository/     Transactional data access and app settings
  domain/           Testable medication, schedule, stock, and dose logic
  notifications/    Notification helper, action receiver, WorkManager worker, rescheduler
  ui/               Compose screens, view models, navigation, formatting
  utils/            Validation helpers
```

Application wiring is handled by:

```text
AppGraph.kt
MediTrackApp.kt
MainActivity.kt
```

`AppGraph` initializes the Room database, repositories, and reminder scheduler. `MediTrackApp` creates notification channels, pre-generates upcoming dose events, and reschedules reminders on app start.

## Data Model

### Medication

Room entity:

```text
data/local/entity/MedicationEntity.kt
```

Medication stores:

- `id`
- `name`
- `dosageInstruction`
- `doseAmount`
- `doseUnit`
- `treatmentType`
- `startDate`
- `endDate`
- `currentStock`
- `totalRequiredStock`
- `lowStockThresholdDays`
- `isActive`
- `createdAt`
- `updatedAt`

Supported treatment types:

- `CONTINUOUS`
- `FIXED_COURSE`

Continuous medication does not require an end date. Fixed Course medication requires an end date and calculates the total required stock from actual scheduled dose dates.

### Medication Schedule

Room entity:

```text
data/local/entity/MedicationScheduleEntity.kt
```

Schedule stores:

- `id`
- `medicationId`
- `scheduleType`
- `timeOfDay`
- `doseAmount`
- `intervalValue`
- `intervalUnit`
- `daysOfWeek`
- `dayOfMonth`
- `isActive`

Supported schedule types:

- `SPECIFIC_TIMES`
- `HOURLY_INTERVAL`
- `DAILY_INTERVAL`
- `WEEKLY_INTERVAL`
- `MONTHLY_INTERVAL`

`doseAmount` is optional on a schedule. When present, it represents how many units should be taken at that specific reminder time. This supports prescription patterns like `1+0+1` and `2+0+1`. If it is not present, the app falls back to the medication's base dose amount.

### Dose Event

Room entity:

```text
data/local/entity/DoseEventEntity.kt
```

Dose event stores:

- `id`
- `medicationId`
- `scheduledDateTime`
- `status`
- `takenDateTime`
- `skippedDateTime`
- `doseAmount`
- `note`

Supported statuses:

- `PENDING`
- `TAKEN`
- `SKIPPED`
- `MISSED`

The database enforces a unique index on `(medicationId, scheduledDateTime)` so duplicate dose events are ignored.

## Core Logic

### Scheduling

Main file:

```text
domain/ScheduleCalculator.kt
```

Responsibilities:

- Generate dose events for a specific date.
- Generate today's dose events.
- Count actual scheduled doses between two dates.
- Summarize schedules for display.
- Parse and normalize typed reminder times.

Important behavior:

- Specific-time schedules create one dose per saved reminder time.
- Specific-time schedules can carry their own per-time dose amount.
- Hourly schedules continue across midnight from the medication start date and first dose time.
- Daily interval schedules create a dose every N days from the start date.
- Weekly schedules support selected days of week.
- Monthly schedules support a day of month and normalize days that exceed the target month length.

### Inventory and Refill Logic

Main file:

```text
domain/InventoryCalculator.kt
```

Responsibilities:

- `calculateDailyUsage`
- `calculateDaysRemaining`
- `calculateTotalRequiredStockForFixedCourse`
- `shouldShowLowStockWarning`
- `isInsufficientForFixedCourse`
- `estimateRemainingDoses`
- `detectCourseCompletion`
- `totalRequiredDoses`

Important behavior:

- Daily usage is estimated from the active schedules and uses per-time dose amounts when available.
- Days remaining is `currentStock / dailyUsage`.
- If no scheduled usage exists, days remaining is treated as infinite and displayed as "No scheduled usage".
- Low stock warning appears when days remaining is less than or equal to `lowStockThresholdDays`.
- Fixed Course total required stock is calculated from actual generated scheduled dose events between `startDate` and `endDate`, summing the dose amount for each event.
- Fixed Course insufficient stock warning appears when current stock is below calculated required stock.

### Dose Status and Stock Deduction

Main file:

```text
domain/DoseStatusManager.kt
```

Rules:

- Marking a pending/skipped/missed dose as Taken deducts `doseAmount` from medication stock.
- Stock is never allowed to go below 0.
- Marking an already Taken dose as Taken again does not deduct stock again.
- Changing a previously Taken dose to Skipped restores the dose amount back to stock.
- Marking a dose as Skipped does not deduct stock.
- Pending doses older than the grace period can be marked as Missed.

Repository transaction file:

```text
data/repository/MedicationRepository.kt
```

This repository applies dose status changes and stock updates inside Room transactions.

## Repository Behavior

Main file:

```text
data/repository/MedicationRepository.kt
```

Responsibilities:

- Observe active medications.
- Observe medication detail and schedule data.
- Observe today's dose events.
- Save medication and schedules.
- Generate dose events for date ranges.
- Mark doses Taken or Skipped.
- Mark overdue pending doses Missed.
- Disable or delete medication.
- Export local medication data as JSON.

Important behavior:

- Saving a new medication inserts the medication first, then inserts schedules using the generated medication ID.
- Editing a medication preserves the original `createdAt` metadata.
- Editing a medication deletes and recreates schedules, then deletes future pending dose events so new events can be regenerated from the updated schedule.
- Disabling a medication marks it inactive and removes future pending dose events.
- Deleting a medication cascades schedule and dose event deletion through Room foreign keys.
- Dose event generation is idempotent because duplicate events are ignored by the unique index.

## Notifications and Reminder Work

Main files:

```text
notifications/NotificationHelper.kt
notifications/ReminderWorker.kt
notifications/ReminderScheduler.kt
notifications/NotificationActionReceiver.kt
notifications/RescheduleReceiver.kt
```

How reminders work:

1. The app generates upcoming dose events for the next 7 days.
2. `ReminderScheduler` cancels existing reminder work and schedules fresh one-time WorkManager jobs.
3. `ReminderWorker` runs when a dose is due.
4. The worker checks settings and current dose status.
5. If notifications are enabled and the dose is still pending, a local notification is shown.
6. Notification actions allow the user to mark the dose Taken or Skip directly.
7. Notification action receiver updates Room data through the repository and then reschedules reminders.

Android 13+ notification permission is requested in `MainActivity`.

Limitations:

- WorkManager is reliable for deferrable background work, but Android may delay execution. This MVP does not use exact alarms.
- Reminder work is scheduled for the upcoming 7-day window and refreshed on app start, notification action, medication changes, boot, and package replacement.

## UI and UX

Navigation is defined in:

```text
ui/MediTrackAppRoot.kt
```

Primary tabs:

- Today
- Cabinet
- Settings

Current navigation uses a Material 3 app shell:

- A bottom `NavigationBar` is shown on Today, Cabinet, and Settings.
- Each tab uses a recognizable Material icon plus an always-visible label.
- Selected tabs use a filled indicator, primary text color, and bold label text.
- Add/Edit and Detail screens hide the bottom bar so the task stays focused.
- A prominent floating `Add Medicine` button appears on Today and Cabinet.
- Screen changes use short fade and slide transitions through Navigation Compose.
- Labels are always visible.
- Medication detail screens keep Inventory selected because they are part of the medication inventory flow.

Theme behavior:

- `System` follows the device light/dark setting.
- `Light` forces the light palette.
- `Dark` forces the dark palette.
- Warning and alert surfaces use Material color roles so they remain readable in both light and dark themes.

### Today Screen

Files:

```text
ui/dashboard/DashboardScreen.kt
ui/dashboard/DashboardViewModel.kt
```

Purpose:

- Show today's scheduled dose events.
- Group doses into time cards instead of one card per medication dose.
- Show high-visibility stock/refill alerts at the top.
- Let users mark doses Taken or Skip.

UX details:

- Morning, Noon/Afternoon, and Night each appear as a single card when they have reminders.
- Each time card contains all medications due at that time.
- Custom reminder times appear as their own "Custom time" card with the actual due time.
- Taken and Skip buttons are large and full-width within each medication row.
- Alert cards use stronger color backgrounds and headings:
  - Critical out-of-stock alerts show "Needs attention now".
  - Refill warnings show "Refill reminders".
- Warning text uses plain wording like "Refill soon".
- Empty state prompts the user to add a medication.
- The app-level floating action button gives users one stable place to add medicine from Today or Cabinet.

Common prescription-time mapping:

```text
Morning           -> 08:00
Noon / Afternoon  -> 14:00
Night             -> 22:00
Custom time       -> any other configured reminder time
```

### Inventory Cabinet

Files:

```text
ui/inventory/InventoryScreen.kt
ui/inventory/InventoryViewModel.kt
```

Purpose:

- Show active medications and stock status.
- Show days remaining.
- Show treatment type.
- Show low-stock, out-of-stock, and course-complete badges.
- Allow Details, Edit, Disable, and Delete actions.

UX details:

- Primary row actions are larger and easier to tap.
- Disable/Delete require a second confirm tap.
- Fixed Course cards show required stock, remaining doses, and purchase warnings.

### Add/Edit Medication

Files:

```text
ui/addedit/AddEditMedicationScreen.kt
ui/addedit/AddEditMedicationViewModel.kt
```

Purpose:

- Add or edit medication records.
- Capture medicine name, unit, stock, treatment type, course length, prescription pattern, and low-stock threshold.

UX details:

- The primary flow matches common prescription wording such as `1+0+1`.
- Morning, Afternoon, and Night are shown as separate dose quantity fields.
- `1+0+1`, `1+1+1`, and `0+0+1` presets are available.
- For Fixed Course medication, users enter a course length such as 7 Days, 2 Weeks, or 1 Month.
- Fixed Course end date is automatically calculated from the start date and course length.
- Estimated full-course stock requirement is shown for simple prescription patterns.
- Material date pickers are still used for the start date and optional continuous-treatment end date.
- Advanced schedule is available behind a switch for hourly, daily interval, weekly, or monthly plans.
- Validation errors are shown in plain language.

Validation:

- Medication name is required.
- Dose amount must be greater than 0.
- Current stock cannot be negative.
- Fixed Course medication requires a course length.
- Fixed Course end date is auto-calculated and must be on or after start date.
- At least one schedule rule is required.
- Simple prescription pattern must have at least one non-zero Morning, Afternoon, or Night dose.
- Specific-time schedules require valid reminder times.
- Invalid typed reminder times are rejected instead of silently dropped.
- Weekly schedule days must be valid day numbers or day names.
- Monthly day must be between 1 and 31.

Simple prescription behavior:

```text
Morning dose    -> 08:00 reminder
Afternoon dose  -> 14:00 reminder
Night dose      -> 22:00 reminder
```

Only dose values greater than 0 generate reminders. For example, `1+0+1` creates morning and night dose events, and each event deducts 1 unit from stock when marked Taken.

### Medication Detail

Files:

```text
ui/detail/MedicationDetailScreen.kt
ui/detail/MedicationDetailViewModel.kt
```

Purpose:

- Show full medication details.
- Show current stock and days remaining.
- Show schedule summary.
- Show refill warnings.
- Show Fixed Course progress.
- Show dose history.

### Settings

Files:

```text
ui/settings/SettingsScreen.kt
ui/settings/SettingsViewModel.kt
```

Purpose:

- Choose the app theme: System, Light, or Dark.
- Configure default low-stock threshold.
- Toggle notifications.
- Toggle vibration.
- Export local data as JSON.
- Clear all data with confirmation.

UX details:

- Theme choices are shown as three large segmented-style buttons.
- The selected theme is filled; unselected choices are outlined.
- Settings messages and warnings use readable Material alert surfaces.

Export behavior:

- JSON export is generated locally.
- Android's document picker lets the user save the JSON file to a chosen location.

## Offline-First Behavior

All core app data is stored on-device in Room:

```text
data/local/AppDatabase.kt
```

The app does not require internet access to:

- Add medication.
- View medication.
- Track doses.
- Deduct stock.
- Calculate remaining stock.
- Show local reminders.
- Export local data.

No backend service is used.

## Build and Test

Local test and build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
```

GitHub Actions:

```text
.github/workflows/android-debug-apk.yml
```

Workflow behavior:

- Runs on push to `main` and `master`.
- Supports manual `workflow_dispatch`.
- Sets up JDK 17.
- Sets up Android SDK.
- Runs JVM unit tests.
- Builds the debug APK.
- Uploads the APK as a GitHub Actions artifact.
- Does not run emulator tests.

## Tests

Test file:

```text
app/src/test/java/com/meditrack/domain/MedicationBusinessLogicTest.kt
```

Covered logic:

- Taken dose deducts stock.
- Skipped dose does not deduct stock.
- Stock never goes below 0.
- Changing Taken to Skipped restores stock.
- Continuous medication days remaining calculation.
- Fixed Course total stock calculation.
- Fixed Course exact dose counting for daily interval schedules.
- Fixed Course insufficient stock warning.
- Low-stock warning threshold.
- Course complete detection.
- Multiple daily reminder times.
- Hourly interval schedules across midnight.
- Prescription-style per-time dose amounts and Fixed Course stock requirements.

## Current Known Limitations

- WorkManager reminders may be delayed by Android battery and background execution rules.
- The app does not use exact alarms.
- There are no instrumented UI tests yet.
- There is no database migration strategy beyond destructive migration in the MVP.
- The current schema has been bumped to version 2 to support per-schedule dose amounts.
- There is no encrypted database layer.
- Dose history exists, but advanced analytics and charts are not implemented.
- Refill entry is represented by editing current stock; there is no separate refill transaction history yet.
- There is no multi-user support.
- There is no cloud sync by design.

## Future Improvement Ideas

High-value next steps:

- Add a simple refill flow that records refill history separately from medication edits.
- Allow custom Morning/Afternoon/Night reminder times from Settings.
- Add exact alarm support as an optional reminder mode with proper Android permission handling.
- Add accessibility review with font scale, TalkBack labels, and contrast verification.
- Add instrumented Compose UI tests for key flows.
- Add Room migrations before releasing beyond MVP.
- Add a missed-dose policy setting for grace period length.
- Add recurring reminder horizon refresh work so reminders stay scheduled even if the app is not opened for more than 7 days.
- Add search/filter in Inventory for users with many medications.
- Add export/import support for full local backup and restore.

## Important Files Quick Reference

```text
AppGraph.kt
MediTrackApp.kt
MainActivity.kt
data/local/AppDatabase.kt
data/local/dao/DoseEventDao.kt
data/local/dao/MedicationDao.kt
data/local/dao/ScheduleDao.kt
data/repository/MedicationRepository.kt
domain/ScheduleCalculator.kt
domain/InventoryCalculator.kt
domain/DoseStatusManager.kt
notifications/ReminderScheduler.kt
notifications/ReminderWorker.kt
notifications/NotificationActionReceiver.kt
ui/MediTrackAppRoot.kt
ui/dashboard/DashboardScreen.kt
ui/inventory/InventoryScreen.kt
ui/addedit/AddEditMedicationScreen.kt
ui/detail/MedicationDetailScreen.kt
ui/settings/SettingsScreen.kt
```
