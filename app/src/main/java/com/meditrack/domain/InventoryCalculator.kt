package com.meditrack.domain

import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

object InventoryCalculator {
    fun calculateDailyUsage(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>
    ): Double {
        val activeSchedules = schedules.filter { it.isActive }
        return activeSchedules.sumOf { schedule ->
            val doseAmount = schedule.doseAmount?.takeIf { it > 0.0 } ?: medication.doseAmount
            expectedDosesPerDay(schedule) * doseAmount
        }
    }

    fun calculateDaysRemaining(currentStock: Double, dailyUsage: Double): Double {
        if (currentStock <= 0.0) return 0.0
        if (dailyUsage <= 0.0) return Double.POSITIVE_INFINITY
        return currentStock / dailyUsage
    }

    fun calculateTotalRequiredStockForFixedCourse(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>
    ): Double {
        if (medication.treatmentType != TreatmentType.FIXED_COURSE || medication.endDate == null) {
            return 0.0
        }
        val requiredStock = ScheduleCalculator.sumScheduledDoseAmountBetween(
            medication = medication,
            schedules = schedules,
            startDate = medication.startDate,
            endDateInclusive = medication.endDate
        )
        return roundStock(requiredStock)
    }

    fun shouldShowLowStockWarning(daysRemaining: Double, thresholdDays: Double): Boolean {
        return daysRemaining <= max(0.0, thresholdDays)
    }

    fun isInsufficientForFixedCourse(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
        takenDoseAmount: Double? = null
    ): Boolean {
        if (medication.treatmentType != TreatmentType.FIXED_COURSE) return false
        val required = medication.totalRequiredStock
            ?: calculateTotalRequiredStockForFixedCourse(medication, schedules)
        val remainingRequired = takenDoseAmount
            ?.let { taken -> max(0.0, required - max(0.0, taken)) }
            ?: required
        return remainingRequired > 0.0 && medication.currentStock + STOCK_EPSILON < remainingRequired
    }

    fun estimateRemainingDoses(medication: MedicationEntity): Int {
        if (medication.doseAmount <= 0.0) return 0
        return floor(medication.currentStock / medication.doseAmount).toInt()
    }

    fun buildSummary(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
        takenDoseCount: Int? = null,
        takenDoseAmount: Double? = null,
        today: LocalDate = LocalDate.now()
    ): MedicationSummary {
        val dailyUsage = calculateDailyUsage(medication, schedules)
        val daysRemaining = calculateDaysRemaining(medication.currentStock, dailyUsage)
        val totalRequiredDoses = totalRequiredDoses(medication, schedules)
        val courseComplete = detectCourseCompletion(medication, schedules, takenDoseCount, today)
        return MedicationSummary(
            dailyUsage = dailyUsage,
            daysRemaining = daysRemaining,
            lowStock = shouldShowLowStockWarning(daysRemaining, medication.lowStockThresholdDays),
            outOfStock = medication.currentStock <= 0.0,
            remainingDoses = totalRequiredDoses?.let { required ->
                if (takenDoseCount == null) required else max(0, required - takenDoseCount)
            },
            courseComplete = courseComplete,
            insufficientStockForCourse = isInsufficientForFixedCourse(
                medication,
                schedules,
                takenDoseAmount
            )
        )
    }

    fun detectCourseCompletion(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
        takenDoseCount: Int? = null,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        if (medication.treatmentType != TreatmentType.FIXED_COURSE) return false
        val requiredDoses = totalRequiredDoses(medication, schedules) ?: return false
        if (takenDoseCount != null) return takenDoseCount >= requiredDoses
        val endDate = medication.endDate ?: return false
        return today.isAfter(endDate) && medication.currentStock <= 0.0
    }

    fun totalRequiredDoses(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>
    ): Int? {
        if (medication.treatmentType != TreatmentType.FIXED_COURSE || medication.endDate == null) {
            return null
        }
        if (medication.doseAmount <= 0.0) return 0
        return ScheduleCalculator.countScheduledDosesBetween(
            medication = medication,
            schedules = schedules,
            startDate = medication.startDate,
            endDateInclusive = medication.endDate
        )
    }

    private fun expectedDosesPerDay(schedule: MedicationScheduleEntity): Double {
        val interval = schedule.intervalValue?.takeIf { it > 0 } ?: 1
        return when (schedule.scheduleType) {
            ScheduleType.SPECIFIC_TIMES -> 1.0
            ScheduleType.HOURLY_INTERVAL -> 24.0 / interval.toDouble()
            ScheduleType.DAILY_INTERVAL -> 1.0 / interval.toDouble()
            ScheduleType.WEEKLY_INTERVAL -> {
                val selectedDays = parseDaysOfWeek(schedule.daysOfWeek).ifEmpty { setOf(1) }
                selectedDays.size.toDouble() / (7.0 * interval.toDouble())
            }
            ScheduleType.MONTHLY_INTERVAL -> 1.0 / (30.4375 * interval.toDouble())
        }
    }

    private fun roundStock(value: Double): Double = ceil(value * 100.0) / 100.0

    private const val STOCK_EPSILON = 0.000_001

    fun parseDaysOfWeek(value: String?): Set<Int> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(",")
            .mapNotNull { token ->
                val trimmed = token.trim()
                trimmed.toIntOrNull()?.takeIf { it in 1..7 }
                    ?: dayNameToNumber(trimmed)
            }
            .toSet()
    }

    private fun dayNameToNumber(value: String): Int? {
        return when (value.lowercase().take(3)) {
            "mon" -> 1
            "tue" -> 2
            "wed" -> 3
            "thu" -> 4
            "fri" -> 5
            "sat" -> 6
            "sun" -> 7
            else -> null
        }
    }
}
