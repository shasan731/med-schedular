package com.meditrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.meditrack.data.local.dao.DoseEventDao
import com.meditrack.data.local.dao.MedicationDao
import com.meditrack.data.local.dao.ScheduleDao
import com.meditrack.data.local.dao.VaccinationDao
import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.data.local.entity.VaccinationEntity

@Database(
    entities = [
        MedicationEntity::class,
        MedicationScheduleEntity::class,
        DoseEventEntity::class,
        VaccinationEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseEventDao(): DoseEventDao
    abstract fun vaccinationDao(): VaccinationDao
}
