package com.meditrack.domain

import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.IntervalUnit
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MedicationBusinessLogicTest {
    @Test
    fun markingDoseTakenDeductsStock() {
        val medication = medication(currentStock = 10.0)
        val dose = doseEvent(doseAmount = 1.0)

        val (updatedMedication, updatedDose) = DoseStatusManager.markDoseTaken(medication, dose)

        assertEquals(9.0, updatedMedication.currentStock, 0.0)
        assertEquals(DoseStatus.TAKEN, updatedDose.status)
    }

    @Test
    fun markingDoseSkippedDoesNotDeductStock() {
        val dose = doseEvent(doseAmount = 1.0)

        val updatedDose = DoseStatusManager.markDoseSkipped(dose)

        assertEquals(DoseStatus.SKIPPED, updatedDose.status)
        assertEquals(1.0, updatedDose.doseAmount, 0.0)
    }

    @Test
    fun changingTakenDoseToSkippedRestoresStock() {
        val medication = medication(currentStock = 9.0)
        val dose = doseEvent(doseAmount = 1.0).copy(status = DoseStatus.TAKEN)

        val (updatedMedication, updatedDose) = DoseStatusManager.markDoseSkipped(medication, dose)

        assertEquals(10.0, updatedMedication.currentStock, 0.0)
        assertEquals(DoseStatus.SKIPPED, updatedDose.status)
    }

    @Test
    fun stockNeverGoesBelowZero() {
        val medication = medication(currentStock = 0.5)
        val dose = doseEvent(doseAmount = 1.0)

        val (updatedMedication, _) = DoseStatusManager.markDoseTaken(medication, dose)

        assertEquals(0.0, updatedMedication.currentStock, 0.0)
    }

    @Test
    fun continuousMedicationCalculatesDaysRemaining() {
        val medication = medication(currentStock = 10.0, doseAmount = 1.0)
        val schedules = specificTimes("08:00", "20:00")

        val dailyUsage = InventoryCalculator.calculateDailyUsage(medication, schedules)
        val daysRemaining = InventoryCalculator.calculateDaysRemaining(medication.currentStock, dailyUsage)

        assertEquals(2.0, dailyUsage, 0.0)
        assertEquals(5.0, daysRemaining, 0.0)
    }

    @Test
    fun fixedCourseCalculatesTotalRequiredStock() {
        val medication = medication(
            treatmentType = TreatmentType.FIXED_COURSE,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 5),
            doseAmount = 1.0
        )
        val schedules = specificTimes("08:00", "20:00")

        val required = InventoryCalculator.calculateTotalRequiredStockForFixedCourse(medication, schedules)

        assertEquals(10.0, required, 0.0)
    }

    @Test
    fun fixedCourseUsesActualDoseDatesForDailyIntervals() {
        val medication = medication(
            treatmentType = TreatmentType.FIXED_COURSE,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 5),
            doseAmount = 1.0
        )
        val schedules = listOf(
            MedicationScheduleEntity(
                medicationId = 1,
                scheduleType = ScheduleType.DAILY_INTERVAL,
                timeOfDay = "09:00",
                intervalValue = 2,
                intervalUnit = IntervalUnit.DAYS
            )
        )

        val required = InventoryCalculator.calculateTotalRequiredStockForFixedCourse(medication, schedules)

        assertEquals(3.0, required, 0.0)
    }

    @Test
    fun hourlyIntervalCarriesAcrossMidnight() {
        val medication = medication(startDate = LocalDate.of(2026, 1, 1))
        val schedules = listOf(
            MedicationScheduleEntity(
                medicationId = 1,
                scheduleType = ScheduleType.HOURLY_INTERVAL,
                timeOfDay = "22:00",
                intervalValue = 8,
                intervalUnit = IntervalUnit.HOURS
            )
        )

        val events = ScheduleCalculator.generateDoseEventsForDate(
            medication = medication,
            schedules = schedules,
            date = LocalDate.of(2026, 1, 2)
        )

        assertEquals(listOf(6, 14, 22), events.map { it.scheduledDateTime.hour })
    }

    @Test
    fun fixedCourseFlagsInsufficientStock() {
        val medication = medication(
            currentStock = 8.0,
            treatmentType = TreatmentType.FIXED_COURSE,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 5)
        )
        val schedules = specificTimes("08:00", "20:00")

        assertTrue(InventoryCalculator.isInsufficientForFixedCourse(medication, schedules))
    }

    @Test
    fun lowStockWarningAppearsWhenOneDayRemains() {
        assertTrue(InventoryCalculator.shouldShowLowStockWarning(daysRemaining = 1.0, thresholdDays = 1.0))
        assertFalse(InventoryCalculator.shouldShowLowStockWarning(daysRemaining = 1.1, thresholdDays = 1.0))
    }

    @Test
    fun courseCompleteAppearsAfterFinalRequiredDose() {
        val medication = medication(
            treatmentType = TreatmentType.FIXED_COURSE,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 5)
        )
        val schedules = specificTimes("08:00", "20:00")

        assertTrue(
            InventoryCalculator.detectCourseCompletion(
                medication = medication,
                schedules = schedules,
                takenDoseCount = 10
            )
        )
    }

    @Test
    fun multipleDailyReminderTimesCalculateDailyUsage() {
        val medication = medication(doseAmount = 2.0)
        val schedules = specificTimes("08:00", "14:00", "22:00")

        val dailyUsage = InventoryCalculator.calculateDailyUsage(medication, schedules)

        assertEquals(6.0, dailyUsage, 0.0)
    }

    private fun medication(
        currentStock: Double = 10.0,
        doseAmount: Double = 1.0,
        treatmentType: TreatmentType = TreatmentType.CONTINUOUS,
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        endDate: LocalDate? = null
    ): MedicationEntity {
        return MedicationEntity(
            id = 1,
            name = "Paracetamol",
            dosageInstruction = "1 tablet after meal",
            doseAmount = doseAmount,
            doseUnit = "tablet",
            treatmentType = treatmentType,
            startDate = startDate,
            endDate = endDate,
            currentStock = currentStock,
            totalRequiredStock = null,
            lowStockThresholdDays = 1.0
        )
    }

    private fun doseEvent(doseAmount: Double): DoseEventEntity {
        return DoseEventEntity(
            id = 1,
            medicationId = 1,
            scheduledDateTime = LocalDateTime.of(2026, 1, 1, 8, 0),
            doseAmount = doseAmount
        )
    }

    private fun specificTimes(vararg times: String): List<MedicationScheduleEntity> {
        return times.mapIndexed { index, time ->
            MedicationScheduleEntity(
                id = index + 1L,
                medicationId = 1,
                scheduleType = ScheduleType.SPECIFIC_TIMES,
                timeOfDay = time
            )
        }
    }
}
