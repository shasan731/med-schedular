package com.meditrack

import android.content.Context
import androidx.room.Room
import com.meditrack.data.local.AppDatabase
import com.meditrack.data.repository.MedicationRepository
import com.meditrack.data.repository.SettingsRepository
import com.meditrack.data.repository.VaccinationRepository
import com.meditrack.notifications.ReminderScheduler

object AppGraph {
    lateinit var appContext: Context
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var medicationRepository: MedicationRepository
        private set
    lateinit var vaccinationRepository: VaccinationRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set

    fun initialize(context: Context) {
        if (::database.isInitialized) return
        val appContext = context.applicationContext
        this.appContext = appContext
        database = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "meditrack.db"
        )
            .fallbackToDestructiveMigration()
            .build()
        medicationRepository = MedicationRepository(database)
        vaccinationRepository = VaccinationRepository(database)
        settingsRepository = SettingsRepository(appContext)
        reminderScheduler = ReminderScheduler(
            appContext,
            medicationRepository,
            vaccinationRepository,
            settingsRepository
        )
    }
}
