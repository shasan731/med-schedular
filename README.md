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
- English and Bengali language support, switchable in Settings (AndroidX per-app locales)

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
- Refill medication stock by adding units to current stock.
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
notifications/ReminderScheduler.kt
notifications/ReminderReceiver.kt
notifications/ReminderWorker.kt
notifications/NotificationActionReceiver.kt
notifications/RescheduleReceiver.kt
```

How reminders work:

1. The app generates upcoming dose events for the next 7 days.
2. `ReminderScheduler` cancels the alarms it armed last time and schedules a fresh exact alarm for each pending dose event using `AlarmManager` (`setExactAndAllowWhileIdle`). The set of armed dose-event ids is persisted so it can be cancelled cleanly on the next reschedule.
3. When a dose is due, the alarm fires `ReminderReceiver`.
4. The receiver checks settings and current dose status.
5. If notifications are enabled and the dose is still pending, a local notification is shown.
6. Notification actions allow the user to mark the dose Taken or Skip directly.
7. Notification action receiver updates Room data through the repository and then reschedules reminders.

Exact alarms fire on time even in Doze / battery-saver. If the user has revoked the exact-alarm permission (possible on Android 12), the scheduler falls back to `setAndAllowWhileIdle`, which still fires but not exactly on time.

`ReminderWorker` is now a once-a-day periodic WorkManager job that refreshes the dose-event horizon and re-arms the alarms, so reminders keep working even if the app is not opened for a long time or the OS clears alarms.

Permissions:

- Android 13+ notification permission is requested in `MainActivity`.
- `USE_EXACT_ALARM` is declared for Android 13+ (auto-granted for alarm/reminder apps), and `SCHEDULE_EXACT_ALARM` is declared for Android 12 (`maxSdkVersion="32"`).

Limitations:

- Reminders are armed for the upcoming 7-day window and refreshed on app start, resume, notification action, medication changes, boot, package replacement, and the daily periodic job.
- Exact alarms are reliable but aggressive OEM battery managers can still kill background apps; disabling battery optimization for MediTrack is recommended for best reliability.
- Distributing through Google Play with `USE_EXACT_ALARM` requires the app to qualify as an alarm/reminder app (medication reminders qualify).

## UI and UX

Navigation is defined in:

```text
ui/MediTrackAppRoot.kt
```

Primary tabs:

- Today
- Medicines
- Settings

Current navigation uses a Material 3 app shell:

- A bottom `NavigationBar` is shown on Today, Medicines, and Settings.
- Each tab uses a recognizable Material icon plus an always-visible label.
- Selected tabs use a filled indicator, primary text color, and bold label text.
- Add/Edit and Detail screens hide the bottom bar so the task stays focused.
- A prominent floating `Add Medicine` button appears on Today and Medicines.
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
- Each medication row is kept minimal: a large medicine name, a single clear line such as "Take 1 tablet", and large full-width Taken/Skip buttons.
- A status badge is shown only once a dose has been acted on (Taken, Skipped, or Missed); pending doses show no badge to reduce noise.
- A low-stock or out-of-stock badge appears next to the dose line using the same stock status as the alert cards and the Medicines screen.
- Alert cards use stronger color backgrounds and headings:
  - Critical out-of-stock alerts show "Needs attention now".
  - Refill warnings show "Refill reminders".
- Warning text uses plain wording like "Refill soon".
- Stock and refill alerts are only shown for medications that are active today. A finished Fixed Course or a not-yet-started medication does not raise refill or out-of-stock alerts.
- The screen refreshes on resume, so the timeline rolls over to the new day, overdue doses become Missed, and reminders reschedule even if the app was left open.
- Empty state prompts the user to add a medicine.
- The app-level floating action button gives users one stable place to add medicine from Today or Medicines.

Common prescription-time mapping:

```text
Morning           -> 08:00
Noon / Afternoon  -> 14:00
Night             -> 22:00
Custom time       -> any other configured reminder time
```

### Medicines (Inventory)

The Medicines tab (titled "My Medicines" in the app) is the medication inventory.

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
- Allow Refill, Edit, Disable, and Delete actions.

UX details:

- Tapping a card opens the medication detail screen; a chevron hints that the whole card is tappable.
- Primary actions are Refill and Edit; Refill opens a small dialog to add units to current stock without reopening the full form.
- Disable/Delete are secondary and require a second confirm tap.
- Fixed Course cards show required stock, remaining doses, and purchase warnings.
- Adding medicine is done from the single app-wide floating button, so the screen has no duplicate header add button.

### Add/Edit Medication

Files:

```text
ui/addedit/AddEditMedicationScreen.kt
ui/addedit/AddEditMedicationViewModel.kt
```

Purpose:

- Add or edit medication records.
- Capture medicine name, unit, stock, treatment type, course length, prescription pattern, and low-stock threshold.

The screen is written for older and non-technical users: plain-language questions, large tap targets, and as little typing as possible.

UX details:

- Sections read as plain questions: "What is the medicine?", "How long will you take it?", "How much and when?", and "How many do you have?".
- Treatment length is chosen with two clear buttons: "Every day, ongoing" (Continuous) and "For a set number of days" (Fixed Course).
- Morning, Afternoon, and Night doses use large tap-only steppers (a minus button, the number, and a plus button) so no keyboard is needed for the common case. Each stepper shows its reminder time as a subtitle.
- "Quick fill" buttons ("Morning + night", "Night only") set a common pattern in one tap. The underlying schedule still supports any per-time amount such as `1+0+1` or `2+0+1`.
- Dates are chosen from a single large button that shows a human-readable date, for example "Tue, Jul 7, 2026", backed by a Material date picker.
- For Fixed Course medication, users enter a course length such as 7 Days, 2 Weeks, or 1 Month, and the last day is calculated and shown in plain language.
- Estimated full-course stock requirement is shown for simple prescription patterns.
- Advanced schedule is available behind a switch for hourly, daily interval, weekly, or monthly plans, and is off by default.
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
- Refill stock with a single button (adds units to current stock).
- Show Fixed Course progress.
- Show dose history.

### Settings

Files:

```text
ui/settings/SettingsScreen.kt
ui/settings/SettingsViewModel.kt
```

Purpose:

- Choose the app language: English or Bengali.
- Choose the app theme: System, Light, or Dark.
- Configure default low-stock threshold.
- Toggle notifications.
- Toggle vibration.
- Export local data as JSON.
- Clear all data with confirmation.

UX details:

- Language and theme choices are shown as large segmented-style buttons.
- The selected option is filled; unselected choices are outlined.
- Settings messages and warnings use readable Material alert surfaces.

Localization:

- The app ships English (`res/values/strings.xml`) and Bengali (`res/values-bn/strings.xml`) string resources.
- Language switching uses AndroidX per-app locales via `AppCompatDelegate.setApplicationLocales`. The choice is persisted (by AppCompat on Android 12 and below, by the system on Android 13+) and applied app-wide; changing it recreates the activity so the UI reloads in the new language.
- Enum labels map to string resources in `ui/Labels.kt` so the domain enums stay resource-free and unit-testable.
- Known gaps: validation error messages (`ValidationUtils`) and the schedule summary (`ScheduleCalculator`) are still English, and on Android 12 and below a few view-model-generated strings may only fully switch after the app restarts.

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

- Dose reminders use exact alarms and fire on time, but aggressive OEM battery managers can still delay or kill background apps; disabling battery optimization is recommended.
- There are no instrumented UI tests yet.
- There is no database migration strategy beyond destructive migration in the MVP.
- The current schema has been bumped to version 2 to support per-schedule dose amounts.
- There is no encrypted database layer.
- Dose history exists, but advanced analytics and charts are not implemented.
- A basic refill (add to current stock) exists on the Medicines and Detail screens; there is no separate refill transaction history yet.
- There is no multi-user support.
- There is no cloud sync by design.

## Future Improvement Ideas

High-value next steps:

- Extend the refill flow to record refill history separately (a basic refill that adds to current stock already exists on the Medicines and Detail screens).
- Allow custom Morning/Afternoon/Night reminder times from Settings.
- Add an in-app prompt to allow exact alarms (Android 12) and to disable battery optimization for best reliability (exact alarms are already the default reminder mechanism).
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
notifications/ReminderReceiver.kt
notifications/ReminderWorker.kt
notifications/NotificationActionReceiver.kt
ui/MediTrackAppRoot.kt
ui/dashboard/DashboardScreen.kt
ui/inventory/InventoryScreen.kt
ui/addedit/AddEditMedicationScreen.kt
ui/detail/MedicationDetailScreen.kt
ui/settings/SettingsScreen.kt
```
