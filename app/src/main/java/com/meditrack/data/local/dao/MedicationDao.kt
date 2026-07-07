package com.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationWithSchedules
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Transaction
    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeActiveMedicationsWithSchedules(): Flow<List<MedicationWithSchedules>>

    @Transaction
    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    suspend fun getActiveMedicationsWithSchedules(): List<MedicationWithSchedules>

    @Transaction
    @Query("SELECT * FROM medications ORDER BY isActive DESC, name COLLATE NOCASE")
    fun observeAllMedicationsWithSchedules(): Flow<List<MedicationWithSchedules>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeMedicationWithSchedules(id: Long): Flow<MedicationWithSchedules?>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationWithSchedules(id: Long): MedicationWithSchedules?

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedication(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE isActive = 1")
    suspend fun getActiveMedications(): List<MedicationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Delete
    suspend fun deleteMedication(medication: MedicationEntity)

    @Query("UPDATE medications SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun disableMedication(id: Long, updatedAt: java.time.LocalDateTime)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedicationById(id: Long)

    @Query("DELETE FROM medications")
    suspend fun clearAll()
}
