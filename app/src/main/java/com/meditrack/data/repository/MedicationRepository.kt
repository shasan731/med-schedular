package com.meditrack.data.repository

import androidx.room.withTransaction
import com.meditrack.data.local.AppDatabase
import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.data.local.entity.DoseEventWithMedication
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.data.local.entity.MedicationWithSchedules
import com.meditrack.domain.DoseStatusManager
import com.meditrack.domain.InventoryCalculator
import com.meditrack.domain.ScheduleCalculator
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.TreatmentType
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

class MedicationRepository(
    private val database: AppDatabase
) {
    private val medicationDao = database.medicationDao()
    private val scheduleDao = database.scheduleDao()
    private val doseEventDao = database.doseEventDao()

    fun observeActiveMedications(): Flow<List<MedicationWithSchedules>> {
        return medicationDao.observeActiveMedicationsWithSchedules()
    }

    fun observeAllMedications(): Flow<List<MedicationWithSchedules>> {
        return medicationDao.observeAllMedicationsWithSchedules()
    }

    fun observeMedication(id: Long): Flow<MedicationWithSchedules?> {
        return medicationDao.observeMedicationWithSchedules(id)
    }

    fun observeDoseEventsForDate(date: LocalDate): Flow<List<DoseEventWithMedication>> {
        return doseEventDao.observeDoseEventsWithMedication(
            date.atStartOfDay(),
            date.plusDays(1).atStartOfDay()
        )
    }

    fun observeDoseHistory(medicationId: Long): Flow<List<DoseEventEntity>> {
        return doseEventDao.observeDoseHistory(medicationId)
    }

    fun observeAllDoseEvents(): Flow<List<DoseEventEntity>> {
        return doseEventDao.observeAllDoseEvents()
    }

    suspend fun saveMedication(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>
    ): SaveMedicationResult {
        val totalRequired = if (medication.treatmentType == TreatmentType.FIXED_COURSE) {
            InventoryCalculator.calculateTotalRequiredStockForFixedCourse(medication, schedules)
        } else {
            null
        }

        val savedId = database.withTransaction {
            if (medication.id == 0L) {
                val now = LocalDateTime.now()
                val id = medicationDao.insertMedication(
                    medication.copy(
                        totalRequiredStock = totalRequired,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                scheduleDao.insertSchedules(schedules.map { it.copy(medicationId = id) })
                id
            } else {
                val now = LocalDateTime.now()
                val existing = medicationDao.getMedication(medication.id)
                medicationDao.updateMedication(
                    medication.copy(
                        totalRequiredStock = totalRequired,
                        createdAt = existing?.createdAt ?: medication.createdAt,
                        isActive = existing?.isActive ?: medication.isActive,
                        updatedAt = now
                    )
                )
                scheduleDao.deleteSchedulesForMedication(medication.id)
                scheduleDao.insertSchedules(schedules.map { it.copy(id = 0, medicationId = medication.id) })
                doseEventDao.deletePendingDoseEventsForMedicationAfter(
                    medication.id,
                    LocalDateTime.now(),
                    DoseStatus.PENDING
                )
                medication.id
            }
        }

        refreshDoseEventsForRange(LocalDate.now(), LocalDate.now().plusDays(7))
        return SaveMedicationResult(
            medicationId = savedId,
            totalRequiredStock = totalRequired,
            insufficientStockForCourse = totalRequired != null && medication.currentStock < totalRequired
        )
    }

    suspend fun refillMedication(medicationId: Long, addedAmount: Double) {
        if (addedAmount <= 0.0) return
        database.withTransaction {
            val medication = medicationDao.getMedication(medicationId) ?: return@withTransaction
            medicationDao.updateMedication(
                medication.copy(
                    currentStock = medication.currentStock + addedAmount,
                    updatedAt = LocalDateTime.now()
                )
            )
        }
    }

    suspend fun disableMedication(medicationId: Long) {
        database.withTransaction {
            val now = LocalDateTime.now()
            medicationDao.disableMedication(medicationId, now)
            doseEventDao.deletePendingDoseEventsForMedicationAfter(
                medicationId = medicationId,
                cutoff = now,
                pendingStatus = DoseStatus.PENDING
            )
        }
    }

    suspend fun reactivateMedication(medicationId: Long) {
        medicationDao.reactivateMedication(medicationId, LocalDateTime.now())
    }

    suspend fun deleteMedication(medicationId: Long) {
        medicationDao.deleteMedicationById(medicationId)
    }

    suspend fun clearAllData() {
        database.withTransaction {
            doseEventDao.clearAll()
            medicationDao.clearAll()
        }
    }

    suspend fun refreshTodayDoseEvents(date: LocalDate = LocalDate.now()) {
        refreshDoseEventsForRange(date, date)
    }

    suspend fun refreshDoseEventsForRange(startDate: LocalDate, endDateInclusive: LocalDate) {
        database.withTransaction {
            val meds = medicationDao.getActiveMedicationsWithSchedules()
            val events = mutableListOf<DoseEventEntity>()
            var date = startDate
            while (!date.isAfter(endDateInclusive)) {
                meds.forEach { medWithSchedules ->
                    events += ScheduleCalculator.generateDoseEventsForDate(
                        medication = medWithSchedules.medication,
                        schedules = medWithSchedules.schedules,
                        date = date
                    )
                }
                date = date.plusDays(1)
            }
            if (events.isNotEmpty()) {
                doseEventDao.insertDoseEvents(events)
            }
        }
    }

    suspend fun markOverdueDosesMissed(
        now: LocalDateTime = LocalDateTime.now(),
        graceMinutes: Long = 60
    ) {
        doseEventDao.markPendingBeforeAsMissed(
            cutoff = now.minusMinutes(graceMinutes),
            pendingStatus = DoseStatus.PENDING,
            missedStatus = DoseStatus.MISSED
        )
    }

    suspend fun markDoseTaken(doseEventId: Long): DoseActionResult {
        return database.withTransaction {
            val event = doseEventDao.getDoseEvent(doseEventId)
                ?: return@withTransaction DoseActionResult.NotFound
            val medication = medicationDao.getMedication(event.medicationId)
                ?: return@withTransaction DoseActionResult.NotFound
            val (updatedMedication, updatedEvent) = DoseStatusManager.markDoseTaken(medication, event)
            medicationDao.updateMedication(updatedMedication)
            doseEventDao.updateDoseEvent(updatedEvent)
            val takenCount = doseEventDao.countByStatus(medication.id, DoseStatus.TAKEN)
            val withSchedules = medicationDao.getMedicationWithSchedules(medication.id)
            val complete = withSchedules?.let {
                InventoryCalculator.detectCourseCompletion(
                    medication = updatedMedication,
                    schedules = it.schedules,
                    takenDoseCount = takenCount
                )
            } ?: false
            DoseActionResult.Success(
                currentStock = updatedMedication.currentStock,
                outOfStock = updatedMedication.currentStock <= 0.0,
                courseComplete = complete
            )
        }
    }

    suspend fun markDoseSkipped(doseEventId: Long): DoseActionResult {
        return database.withTransaction {
            val event = doseEventDao.getDoseEvent(doseEventId)
                ?: return@withTransaction DoseActionResult.NotFound
            val medication = medicationDao.getMedication(event.medicationId)
                ?: return@withTransaction DoseActionResult.NotFound
            val (updatedMedication, updatedEvent) = DoseStatusManager.markDoseSkipped(medication, event)
            medicationDao.updateMedication(updatedMedication)
            doseEventDao.updateDoseEvent(updatedEvent)
            DoseActionResult.Success(
                currentStock = updatedMedication.currentStock,
                outOfStock = updatedMedication.currentStock <= 0.0,
                courseComplete = false
            )
        }
    }

    suspend fun getDueDosePayload(doseEventId: Long): DueDosePayload? {
        val event = doseEventDao.getDoseEvent(doseEventId) ?: return null
        val medication = medicationDao.getMedication(event.medicationId) ?: return null
        if (!medication.isActive) return null
        return DueDosePayload(event, medication)
    }

    suspend fun upcomingPendingDoseEvents(
        now: LocalDateTime = LocalDateTime.now(),
        through: LocalDateTime = now.plusDays(7)
    ): List<DoseEventEntity> {
        return doseEventDao.getDoseEventsForRange(now, through)
            .filter { it.status == DoseStatus.PENDING }
    }

    suspend fun exportLocalDataAsJson(settings: AppSettings? = null): String {
        val medications = medicationDao.getAllMedicationsWithSchedules()
        val events = doseEventDao.getAllDoseEvents()
        val vaccinations = database.vaccinationDao().getAllVaccinations()

        return JSONObject()
            .put("formatVersion", 1)
            .put("exportedAt", LocalDateTime.now().toString())
            .put("medications", JSONArray(medications.map { it.toJson() }))
            .put("doseEvents", JSONArray(events.map { it.toJson() }))
            .put("vaccinations", JSONArray(vaccinations.map { vaccination ->
                JSONObject()
                    .put("id", vaccination.id)
                    .put("name", vaccination.name)
                    .put("doseLabel", vaccination.doseLabel)
                    .put("scheduledDateTime", vaccination.scheduledDateTime.toString())
                    .put("status", vaccination.status.name)
                    .put("completedDateTime", vaccination.completedDateTime?.toString())
                    .put("note", vaccination.note)
                    .put("createdAt", vaccination.createdAt.toString())
                    .put("updatedAt", vaccination.updatedAt.toString())
            }))
            .apply {
                settings?.let {
                    put(
                        "settings",
                        JSONObject()
                            .put("defaultLowStockThresholdDays", it.defaultLowStockThresholdDays)
                            .put("notificationsEnabled", it.notificationsEnabled)
                            .put("vibrationEnabled", it.vibrationEnabled)
                            .put("alarmSoundEnabled", it.alarmSoundEnabled)
                            .put("themeMode", it.themeMode.name)
                    )
                }
            }
            .toString(2)
    }

    private fun MedicationWithSchedules.toJson(): JSONObject {
        return JSONObject()
            .put("id", medication.id)
            .put("name", medication.name)
            .put("dosageInstruction", medication.dosageInstruction)
            .put("doseAmount", medication.doseAmount)
            .put("doseUnit", medication.doseUnit)
            .put("treatmentType", medication.treatmentType.name)
            .put("startDate", medication.startDate.toString())
            .put("endDate", medication.endDate?.toString())
            .put("currentStock", medication.currentStock)
            .put("totalRequiredStock", medication.totalRequiredStock)
            .put("foodRelation", medication.foodRelation.name)
            .put("lowStockThresholdDays", medication.lowStockThresholdDays)
            .put("isActive", medication.isActive)
            .put("createdAt", medication.createdAt.toString())
            .put("updatedAt", medication.updatedAt.toString())
            .put("schedules", JSONArray(schedules.map { it.toJson() }))
    }

    private fun MedicationScheduleEntity.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("medicationId", medicationId)
            .put("scheduleType", scheduleType.name)
            .put("timeOfDay", timeOfDay)
            .put("doseAmount", doseAmount)
            .put("intervalValue", intervalValue)
            .put("intervalUnit", intervalUnit?.name)
            .put("daysOfWeek", daysOfWeek)
            .put("dayOfMonth", dayOfMonth)
            .put("isActive", isActive)
    }

    private fun DoseEventEntity.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("medicationId", medicationId)
            .put("scheduledDateTime", scheduledDateTime.toString())
            .put("status", status.name)
            .put("takenDateTime", takenDateTime?.toString())
            .put("skippedDateTime", skippedDateTime?.toString())
            .put("doseAmount", doseAmount)
            .put("deductedAmount", deductedAmount)
            .put("note", note)
    }
}

data class SaveMedicationResult(
    val medicationId: Long,
    val totalRequiredStock: Double?,
    val insufficientStockForCourse: Boolean
)

data class DueDosePayload(
    val doseEvent: DoseEventEntity,
    val medication: MedicationEntity
)

sealed interface DoseActionResult {
    data object NotFound : DoseActionResult
    data class Success(
        val currentStock: Double?,
        val outOfStock: Boolean,
        val courseComplete: Boolean
    ) : DoseActionResult
}
