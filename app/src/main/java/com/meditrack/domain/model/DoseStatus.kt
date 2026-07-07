package com.meditrack.domain.model

enum class DoseStatus(val label: String) {
    PENDING("Pending"),
    TAKEN("Taken"),
    SKIPPED("Skipped"),
    MISSED("Missed")
}
