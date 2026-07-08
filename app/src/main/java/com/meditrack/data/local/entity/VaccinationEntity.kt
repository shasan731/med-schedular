package com.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meditrack.domain.model.VaccinationStatus
import java.time.LocalDateTime

/**
 * A scheduled vaccination / shot. Unlike a medication it has no stock or recurring schedule; it is a
 * single dated event (e.g. "Covid-19 – Booster") that the user is reminded about and marks done.
 */
@Entity(tableName = "vaccinations")
data class VaccinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val doseLabel: String = "",
    val scheduledDateTime: LocalDateTime,
    val status: VaccinationStatus = VaccinationStatus.UPCOMING,
    val completedDateTime: LocalDateTime? = null,
    val note: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
