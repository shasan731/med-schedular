package com.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meditrack.domain.model.TreatmentType
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosageInstruction: String,
    val doseAmount: Double,
    val doseUnit: String,
    val treatmentType: TreatmentType,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val currentStock: Double,
    val totalRequiredStock: Double?,
    val lowStockThresholdDays: Double = 1.0,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
