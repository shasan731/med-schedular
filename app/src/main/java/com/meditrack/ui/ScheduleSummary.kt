package com.meditrack.ui

import android.content.Context
import com.meditrack.R
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.InventoryCalculator
import com.meditrack.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Localized, human-readable summary of a medication's active schedules. Lives in the UI layer so the
 * pure/testable [com.meditrack.domain.ScheduleCalculator] does not need Android resources.
 */
fun scheduleSummaryText(context: Context, schedules: List<MedicationScheduleEntity>): String {
    val active = schedules.filter { it.isActive }
    if (active.isEmpty()) return context.getString(R.string.sched_summary_none)
    return active.groupBy { it.scheduleType }.entries.joinToString("; ") { (type, items) ->
        val interval = items.first().intervalValue ?: 1
        when (type) {
            ScheduleType.SPECIFIC_TIMES -> {
                val times = items.mapNotNull { it.timeOfDay }.sorted().joinToString(", ")
                context.getString(R.string.sched_summary_specific, times)
            }
            ScheduleType.HOURLY_INTERVAL -> context.getString(R.string.sched_summary_hourly, interval)
            ScheduleType.DAILY_INTERVAL -> context.getString(R.string.sched_summary_daily, interval)
            ScheduleType.WEEKLY_INTERVAL -> context.getString(
                R.string.sched_summary_weekly,
                interval,
                formatDays(context, items.first().daysOfWeek)
            )
            ScheduleType.MONTHLY_INTERVAL -> context.getString(
                R.string.sched_summary_monthly,
                interval,
                items.first().dayOfMonth ?: 1
            )
        }
    }
}

private fun formatDays(context: Context, value: String?): String {
    val days = InventoryCalculator.parseDaysOfWeek(value)
    if (days.isEmpty()) return context.getString(R.string.sched_summary_selected_days)
    return days.sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
