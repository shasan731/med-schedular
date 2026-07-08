package com.meditrack.ui

import androidx.annotation.StringRes
import com.meditrack.R
import com.meditrack.data.repository.ThemeMode
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.FoodRelation
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import com.meditrack.domain.model.VaccinationStatus
import com.meditrack.ui.addedit.CourseDurationUnit

/**
 * Maps domain/UI enums to localized string resources. Kept here (UI layer) so the enums themselves
 * stay free of Android resource dependencies and remain unit-testable.
 */
@StringRes
fun DoseStatus.labelRes(): Int = when (this) {
    DoseStatus.PENDING -> R.string.status_pending
    DoseStatus.TAKEN -> R.string.status_taken
    DoseStatus.SKIPPED -> R.string.status_skipped
    DoseStatus.MISSED -> R.string.status_missed
}

@StringRes
fun ScheduleType.labelRes(): Int = when (this) {
    ScheduleType.SPECIFIC_TIMES -> R.string.sched_specific
    ScheduleType.HOURLY_INTERVAL -> R.string.sched_hourly
    ScheduleType.DAILY_INTERVAL -> R.string.sched_daily
    ScheduleType.WEEKLY_INTERVAL -> R.string.sched_weekly
    ScheduleType.MONTHLY_INTERVAL -> R.string.sched_monthly
}

@StringRes
fun TreatmentType.labelRes(): Int = when (this) {
    TreatmentType.CONTINUOUS -> R.string.treatment_continuous_label
    TreatmentType.FIXED_COURSE -> R.string.treatment_fixed_label
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
fun VaccinationStatus.labelRes(): Int = when (this) {
    VaccinationStatus.UPCOMING -> R.string.vacc_status_upcoming
    VaccinationStatus.DONE -> R.string.vacc_status_done
    VaccinationStatus.MISSED -> R.string.vacc_status_missed
}

@StringRes
fun FoodRelation.labelRes(): Int = when (this) {
    FoodRelation.NONE -> R.string.food_any
    FoodRelation.BEFORE_FOOD -> R.string.food_before
    FoodRelation.AFTER_FOOD -> R.string.food_after
    FoodRelation.WITH_FOOD -> R.string.food_with
}

@StringRes
fun CourseDurationUnit.labelRes(): Int = when (this) {
    CourseDurationUnit.DAYS -> R.string.unit_days
    CourseDurationUnit.WEEKS -> R.string.unit_weeks
    CourseDurationUnit.MONTHS -> R.string.unit_months
}
