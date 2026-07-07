package com.meditrack.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.data.local.entity.DoseEventWithMedication
import com.meditrack.domain.model.DoseStatus
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.StatusBadge
import com.meditrack.ui.components.WarningBand
import com.meditrack.ui.displayTime
import com.meditrack.ui.stockText

@Composable
fun DashboardScreen(
    onAddMedication: () -> Unit,
    onMedicationClick: (Long) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val grouped = state.doses.groupBy { timeCategory(it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = "Today",
                subtitle = state.todayLabel,
                actionLabel = "Add",
                onAction = onAddMedication
            )
        }

        items(state.lowStockWarnings) { warning ->
            WarningBand(
                text = warning,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (state.doses.isEmpty()) {
            item {
                EmptyTodayState(onAddMedication)
            }
        } else {
            listOf("Morning", "Afternoon", "Night", "Other").forEach { group ->
                val doses = grouped[group].orEmpty()
                if (doses.isNotEmpty()) {
                    item {
                        Text(
                            text = group,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(doses, key = { it.id }) { dose ->
                        DoseCard(
                            dose = dose,
                            onMedicationClick = { onMedicationClick(dose.medicationId) },
                            onTaken = { viewModel.markTaken(dose.id) },
                            onSkip = { viewModel.skip(dose.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTodayState(onAddMedication: () -> Unit) {
    BasicCard(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("No medication is scheduled today.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Add a medication and schedule reminder times to populate this timeline.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddMedication) { Text("Add medication") }
        }
    }
}

@Composable
private fun DoseCard(
    dose: DoseEventWithMedication,
    onMedicationClick: () -> Unit,
    onTaken: () -> Unit,
    onSkip: () -> Unit
) {
    BasicCard(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable(onClick = onMedicationClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dose.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(dose.dosageInstruction, style = MaterialTheme.typography.bodyMedium)
                }
                Text(dose.scheduledDateTime.displayTime(), fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(
                    text = dose.status.label,
                    color = statusColor(dose.status)
                )
                if (dose.currentStock <= dose.doseAmount) {
                    StatusBadge(text = "Low stock", color = Color(0xFFB42318))
                }
                Text(
                    text = "Stock: ${dose.currentStock.stockText()} ${dose.doseUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            if (dose.status == DoseStatus.PENDING || dose.status == DoseStatus.MISSED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTaken) { Text("Mark Taken") }
                    OutlinedButton(onClick = onSkip) { Text("Skip") }
                }
            }
        }
    }
}

private fun timeCategory(dose: DoseEventWithMedication): String {
    return when (dose.scheduledDateTime.hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..22 -> "Night"
        else -> "Other"
    }
}

private fun statusColor(status: DoseStatus): Color {
    return when (status) {
        DoseStatus.PENDING -> Color(0xFF355C7D)
        DoseStatus.TAKEN -> Color(0xFF1E7E6F)
        DoseStatus.SKIPPED -> Color(0xFF7A4F01)
        DoseStatus.MISSED -> Color(0xFFB42318)
    }
}
