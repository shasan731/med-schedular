package com.meditrack.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.VaccinationStatus

/**
 * Single source of truth for the semantic status colours used by badges and alerts. Colours are
 * theme-aware: in dark mode the tones are brightened so badge text stays readable on the
 * translucent (14% tint) badge background. Derived from the applied color scheme's surface
 * luminance, so it respects the in-app Light/Dark/System choice, not just the system setting.
 */
@Composable
private fun darkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun dangerColor(): Color = if (darkTheme()) Color(0xFFFF8A80) else Color(0xFFB42318)

@Composable
fun warningColor(): Color = if (darkTheme()) Color(0xFFF6AD55) else Color(0xFFB54708)

@Composable
fun successColor(): Color = if (darkTheme()) Color(0xFF5FD0BC) else Color(0xFF1E7E6F)

@Composable
fun infoColor(): Color = if (darkTheme()) Color(0xFF8FB4D6) else Color(0xFF355C7D)

@Composable
fun doseStatusColor(status: DoseStatus): Color = when (status) {
    DoseStatus.PENDING -> infoColor()
    DoseStatus.TAKEN -> successColor()
    DoseStatus.SKIPPED -> warningColor()
    DoseStatus.MISSED -> dangerColor()
}

@Composable
fun vaccinationStatusColor(status: VaccinationStatus): Color = when (status) {
    VaccinationStatus.UPCOMING -> infoColor()
    VaccinationStatus.DONE -> successColor()
    VaccinationStatus.MISSED -> dangerColor()
}
