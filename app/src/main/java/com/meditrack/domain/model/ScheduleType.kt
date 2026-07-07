package com.meditrack.domain.model

enum class ScheduleType(val label: String) {
    SPECIFIC_TIMES("Specific times"),
    HOURLY_INTERVAL("Hourly interval"),
    DAILY_INTERVAL("Daily interval"),
    WEEKLY_INTERVAL("Weekly interval"),
    MONTHLY_INTERVAL("Monthly interval")
}
