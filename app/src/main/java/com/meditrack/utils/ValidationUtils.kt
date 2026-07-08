package com.meditrack.utils

import com.meditrack.R
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType

object ValidationUtils {
    /**
     * Returns a list of string-resource ids for any validation problems (empty when valid).
     * Resource ids (not resolved strings) keep this class free of a Context so it stays
     * unit-testable and lets the caller resolve them in the user's chosen language.
     */
    fun validateMedication(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>
    ): List<Int> {
        val errors = mutableListOf<Int>()
        if (medication.name.isBlank()) errors += R.string.val_name_required
        if (medication.doseAmount <= 0.0) errors += R.string.val_dose_positive
        if (medication.currentStock < 0.0) errors += R.string.val_stock_negative
        if (medication.treatmentType == TreatmentType.FIXED_COURSE && medication.endDate == null) {
            errors += R.string.val_fixed_needs_end
        }
        if (schedules.isEmpty()) {
            errors += R.string.val_schedule_required
        }
        schedules.forEach { schedule ->
            when (schedule.scheduleType) {
                ScheduleType.SPECIFIC_TIMES -> {
                    if (schedule.timeOfDay.isNullOrBlank()) {
                        errors += R.string.val_specific_time_required
                    }
                }
                ScheduleType.HOURLY_INTERVAL,
                ScheduleType.DAILY_INTERVAL,
                ScheduleType.WEEKLY_INTERVAL,
                ScheduleType.MONTHLY_INTERVAL -> {
                    if ((schedule.intervalValue ?: 0) <= 0 || schedule.intervalUnit == null) {
                        errors += R.string.val_interval_invalid
                    }
                }
            }
        }
        return errors.distinct()
    }
}
