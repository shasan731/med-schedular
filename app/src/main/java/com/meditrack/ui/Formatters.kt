package com.meditrack.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dateInputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun LocalDate.displayDate(): String = format(dateFormatter)
fun LocalDate.inputDate(): String = format(dateInputFormatter)
fun LocalDateTime.displayTime(): String = format(timeFormatter)
fun Double.stockText(): String {
    val rounded = (this * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun Double.daysRemainingText(): String {
    if (isInfinite()) return "No scheduled usage"
    if (this <= 0.0) return "0 days"
    return if (this < 1.0) "<1 day" else "${(this * 10.0).roundToInt() / 10.0} days"
}
