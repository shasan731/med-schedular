package com.meditrack.domain

import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

object ScheduleCalculator {
    private val defaultDoseTime: LocalTime = LocalTime.of(9, 0)

    fun generateTodayDoseEvents(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
        today: LocalDate = LocalDate.now()
    ): List<DoseEventEntity> = generateDoseEventsForDate(medication, schedules, today)

    fun generateDoseEventsForDate(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
        date: LocalDate
    ): List<DoseEventEntity> {
        if (!medication.isActive || !dateWithinTreatment(medication, date)) return emptyList()

        return schedules
            .filter { it.isActive }
            .flatMap { schedule ->
                val doseAmount = schedule.doseAmount?.takeIf { it > 0.0 } ?: medication.doseAmount
                timesForDate(medication.startDate, schedule, date).map { time -> time to doseAmount }
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .map { (time, doseAmount) ->
                DoseEventEntity(
                    medicationId = medication.id,
                    scheduledDateTime = LocalDateTime.of(date, time),
                    doseAmount = doseAmount
                )
            }
    }

    fun countScheduledDosesBetween(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
        startDate: LocalDate,
        endDateInclusive: LocalDate
    ): Int {
        if (endDateInclusive.isBefore(startDate)) return 0
        var count = 0
        var date = startDate
        while (!date.isAfter(endDateInclusive)) {
            count += generateDoseEventsForDate(medication, schedules, date).size
            date = date.plusDays(1)
        }
        return count
    }

    fun sumScheduledDoseAmountBetween(
        medication: MedicationEntity,
        schedules: List<MedicationScheduleEntity>,
        startDate: LocalDate,
        endDateInclusive: LocalDate
    ): Double {
        if (endDateInclusive.isBefore(startDate)) return 0.0
        var total = 0.0
        var date = startDate
        while (!date.isAfter(endDateInclusive)) {
            total += generateDoseEventsForDate(medication, schedules, date).sumOf { it.doseAmount }
            date = date.plusDays(1)
        }
        return total
    }

    fun scheduleSummary(schedules: List<MedicationScheduleEntity>): String {
        val active = schedules.filter { it.isActive }
        if (active.isEmpty()) return "No active schedule"
        return active.groupBy { it.scheduleType }.entries.joinToString("; ") { (type, items) ->
            when (type) {
                ScheduleType.SPECIFIC_TIMES -> {
                    val times = items.mapNotNull { it.timeOfDay }.sorted().joinToString(", ")
                    "Specific times: $times"
                }
                ScheduleType.HOURLY_INTERVAL -> "Every ${items.first().intervalValue ?: 1} hour(s)"
                ScheduleType.DAILY_INTERVAL -> "Every ${items.first().intervalValue ?: 1} day(s)"
                ScheduleType.WEEKLY_INTERVAL -> {
                    val first = items.first()
                    "Every ${first.intervalValue ?: 1} week(s) on ${formatDays(first.daysOfWeek)}"
                }
                ScheduleType.MONTHLY_INTERVAL -> {
                    val first = items.first()
                    "Every ${first.intervalValue ?: 1} month(s) on day ${first.dayOfMonth ?: 1}"
                }
            }
        }
    }

    private fun timesForDate(
        startDate: LocalDate,
        schedule: MedicationScheduleEntity,
        date: LocalDate
    ): List<LocalTime> {
        val interval = schedule.intervalValue?.takeIf { it > 0 } ?: 1
        return when (schedule.scheduleType) {
            ScheduleType.SPECIFIC_TIMES -> listOf(parseTime(schedule.timeOfDay) ?: defaultDoseTime)
            ScheduleType.HOURLY_INTERVAL -> {
                val first = parseTime(schedule.timeOfDay) ?: LocalTime.MIDNIGHT
                intervalTimesForDate(startDate, date, first, interval)
            }
            ScheduleType.DAILY_INTERVAL -> {
                val days = ChronoUnit.DAYS.between(startDate, date)
                if (days >= 0 && days % interval == 0L) {
                    listOf(parseTime(schedule.timeOfDay) ?: defaultDoseTime)
                } else {
                    emptyList()
                }
            }
            ScheduleType.WEEKLY_INTERVAL -> {
                val selectedDays = InventoryCalculator.parseDaysOfWeek(schedule.daysOfWeek)
                    .ifEmpty { setOf(startDate.dayOfWeek.value) }
                val startWeek = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val dateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = ChronoUnit.WEEKS.between(startWeek, dateWeek)
                if (weeks >= 0 && weeks % interval == 0L && date.dayOfWeek.value in selectedDays) {
                    listOf(parseTime(schedule.timeOfDay) ?: defaultDoseTime)
                } else {
                    emptyList()
                }
            }
            ScheduleType.MONTHLY_INTERVAL -> {
                val months = ChronoUnit.MONTHS.between(
                    YearMonth.from(startDate).atDay(1),
                    YearMonth.from(date).atDay(1)
                )
                val day = schedule.dayOfMonth ?: startDate.dayOfMonth
                val normalizedDay = day.coerceAtMost(date.lengthOfMonth())
                if (months >= 0 && months % interval == 0L && date.dayOfMonth == normalizedDay) {
                    listOf(parseTime(schedule.timeOfDay) ?: defaultDoseTime)
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun dateWithinTreatment(medication: MedicationEntity, date: LocalDate): Boolean {
        if (date.isBefore(medication.startDate)) return false
        val endDate = medication.endDate ?: return true
        return !date.isAfter(endDate)
    }

    fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(value.trim()) }.getOrNull()
    }

    fun normalizeTimeInput(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val direct = runCatching { LocalTime.parse(trimmed) }.getOrNull()
        if (direct != null) return direct.toString()
        val compact = trimmed.replace(".", "").uppercase()
        val pattern = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(AM|PM)$""")
        val match = pattern.matchEntire(compact) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 0
        val suffix = match.groupValues[3]
        if (hour !in 1..12 || minute !in 0..59) return null
        if (suffix == "PM" && hour != 12) hour += 12
        if (suffix == "AM" && hour == 12) hour = 0
        return LocalTime.of(hour, minute).toString()
    }

    private fun intervalTimesForDate(
        startDate: LocalDate,
        date: LocalDate,
        firstDoseTime: LocalTime,
        intervalHours: Int
    ): List<LocalTime> {
        val intervalMinutes = intervalHours.coerceAtLeast(1) * 60L
        val anchor = LocalDateTime.of(startDate, firstDoseTime)
        val dayStart = date.atStartOfDay()
        val dayEnd = date.plusDays(1).atStartOfDay()
        if (!dayEnd.isAfter(anchor)) return emptyList()

        val elapsedMinutes = Duration.between(anchor, dayStart).toMinutes()
        val firstStep = if (elapsedMinutes <= 0L) {
            0L
        } else {
            ceil(elapsedMinutes.toDouble() / intervalMinutes.toDouble()).toLong()
        }

        val times = mutableListOf<LocalTime>()
        var occurrence = anchor.plusMinutes(firstStep * intervalMinutes)
        while (occurrence.isBefore(dayEnd)) {
            if (!occurrence.isBefore(dayStart)) {
                times += occurrence.toLocalTime()
            }
            occurrence = occurrence.plusMinutes(intervalMinutes)
        }
        return times
    }

    private fun formatDays(value: String?): String {
        val days = InventoryCalculator.parseDaysOfWeek(value)
        if (days.isEmpty()) return "selected day(s)"
        return days.sorted().joinToString(", ") {
            DayOfWeek.of(it).name.lowercase().replaceFirstChar(Char::titlecase)
        }
    }
}
