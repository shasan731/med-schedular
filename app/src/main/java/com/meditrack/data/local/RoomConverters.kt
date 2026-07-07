package com.meditrack.data.local

import androidx.room.TypeConverter
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.IntervalUnit
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import java.time.LocalDate
import java.time.LocalDateTime

class RoomConverters {
    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localDateTimeToString(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun treatmentTypeToString(value: TreatmentType?): String? = value?.name

    @TypeConverter
    fun stringToTreatmentType(value: String?): TreatmentType? = value?.let(TreatmentType::valueOf)

    @TypeConverter
    fun scheduleTypeToString(value: ScheduleType?): String? = value?.name

    @TypeConverter
    fun stringToScheduleType(value: String?): ScheduleType? = value?.let(ScheduleType::valueOf)

    @TypeConverter
    fun intervalUnitToString(value: IntervalUnit?): String? = value?.name

    @TypeConverter
    fun stringToIntervalUnit(value: String?): IntervalUnit? = value?.let(IntervalUnit::valueOf)

    @TypeConverter
    fun doseStatusToString(value: DoseStatus?): String? = value?.name

    @TypeConverter
    fun stringToDoseStatus(value: String?): DoseStatus? = value?.let(DoseStatus::valueOf)
}
