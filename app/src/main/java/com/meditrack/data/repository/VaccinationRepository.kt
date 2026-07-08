package com.meditrack.data.repository

import com.meditrack.data.local.AppDatabase
import com.meditrack.data.local.entity.VaccinationEntity
import com.meditrack.domain.model.VaccinationStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

class VaccinationRepository(
    database: AppDatabase
) {
    private val dao = database.vaccinationDao()

    fun observeVaccinations(): Flow<List<VaccinationEntity>> = dao.observeVaccinations()

    suspend fun getVaccination(id: Long): VaccinationEntity? = dao.getVaccination(id)

    suspend fun saveVaccination(vaccination: VaccinationEntity): Long {
        val now = LocalDateTime.now()
        return if (vaccination.id == 0L) {
            dao.insert(vaccination.copy(createdAt = now, updatedAt = now))
        } else {
            val existing = dao.getVaccination(vaccination.id)
            dao.update(
                vaccination.copy(
                    createdAt = existing?.createdAt ?: vaccination.createdAt,
                    updatedAt = now
                )
            )
            vaccination.id
        }
    }

    suspend fun markDone(id: Long) {
        val now = LocalDateTime.now()
        dao.updateStatus(id, VaccinationStatus.DONE, now, now)
    }

    suspend fun markUpcoming(id: Long) {
        dao.updateStatus(id, VaccinationStatus.UPCOMING, null, LocalDateTime.now())
    }

    suspend fun markOverdueMissed(now: LocalDateTime = LocalDateTime.now()) {
        dao.markOverdueMissed(now, VaccinationStatus.UPCOMING, VaccinationStatus.MISSED)
    }

    suspend fun deleteVaccination(id: Long) {
        dao.deleteById(id)
    }

    suspend fun upcomingVaccinations(now: LocalDateTime = LocalDateTime.now()): List<VaccinationEntity> {
        return dao.getUpcoming(now.minusMinutes(1), VaccinationStatus.UPCOMING)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
