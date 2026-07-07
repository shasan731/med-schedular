package com.meditrack.ui

import android.content.Context
import com.meditrack.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val longDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val dateInputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun LocalDate.displayDate(): String = format(dateFormatter)
fun LocalDate.longDisplayDate(): String = format(longDateFormatter)
fun LocalDate.inputDate(): String = format(dateInputFormatter)
fun LocalDateTime.displayTime(): String = format(timeFormatter)
fun Double.stockText(): String {
    val rounded = (this * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun Double.daysRemainingText(context: Context): String {
    if (isInfinite()) return context.getString(R.string.days_no_usage)
    if (this <= 0.0) return context.getString(R.string.days_zero)
    if (this < 1.0) return context.getString(R.string.days_less_than_one)
    val rounded = (this * 10.0).roundToInt() / 10.0
    val number = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    val resId = if (rounded == 1.0) R.string.days_one else R.string.days_many
    return context.getString(resId, number)
}
