package com.meditrack.domain

data class MedicationSummary(
    val dailyUsage: Double,
    val daysRemaining: Double,
    val lowStock: Boolean,
    val outOfStock: Boolean,
    val remainingDoses: Int?,
    val courseComplete: Boolean,
    val insufficientStockForCourse: Boolean
)
