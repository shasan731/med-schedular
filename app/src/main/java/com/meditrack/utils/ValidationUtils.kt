package com.meditrack.utils

import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType

object ValidationUtils {
    fun validateMedication(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>
    ): List<String> {
        val errors = mutableListOf<String>()
        if (medication.name.isBlank()) errors += "Medication name is required."
        if (medication.doseAmount <= 0.0) errors += "Dose amount must be greater than 0."
        if (medication.currentStock < 0.0) errors += "Current stock cannot be negative."
        if (medication.treatmentType == TreatmentType.FIXED_COURSE && medication.endDate == null) {
            errors += "Fixed Course medication requires an end date."
        }
        if (schedules.isEmpty()) {
            errors += "At least one schedule rule is required."
        }
        schedules.forEach { schedule ->
            when (schedule.scheduleType) {
                ScheduleType.SPECIFIC_TIMES -> {
                    if (schedule.timeOfDay.isNullOrBlank()) {
                        errors += "Specific-time schedules must include at least one reminder time."
                    }
                }
                ScheduleType.HOURLY_INTERVAL,
                ScheduleType.DAILY_INTERVAL,
                ScheduleType.WEEKLY_INTERVAL,
                ScheduleType.MONTHLY_INTERVAL -> {
                    if ((schedule.intervalValue ?: 0) <= 0 || schedule.intervalUnit == null) {
                        errors += "Interval schedules must have a valid interval value and unit."
                    }
                }
            }
        }
        return errors.distinct()
    }
}
