package com.meditrack.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.TreatmentType
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.StatusBadge
import com.meditrack.ui.daysRemainingText
import com.meditrack.ui.displayTime
import com.meditrack.ui.stockText
import java.time.format.DateTimeFormatter

@Composable
fun MedicationDetailScreen(
    medicationId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MedicationDetailViewModel = viewModel(
        key = "detail-$medicationId",
        factory = MedicationDetailViewModel.factory(medicationId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val medication = state.medication

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = medication?.name ?: "Medication",
                subtitle = medication?.dosageInstruction,
                actionLabel = if (medication != null) "Edit" else null,
                onAction = medication?.let { { onEdit(it.id) } }
            )
        }
        if (medication == null) {
            item {
                BasicCard(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Medication not found.")
                        OutlinedButton(onClick = onBack) { Text("Back") }
                    }
                }
            }
        } else {
            item {
                BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Stock: ${medication.currentStock.stockText()} ${medication.doseUnit}", fontWeight = FontWeight.SemiBold)
                        Text("Days remaining: ${state.summary?.daysRemaining?.daysRemainingText() ?: "Unknown"}")
                        Text("Treatment: ${medication.treatmentType.label}")
                        Text("Schedule: ${state.scheduleSummary}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.summary?.outOfStock == true) StatusBadge("Out of stock", Color(0xFFB42318))
                            if (state.summary?.lowStock == true) StatusBadge("Refill warning", Color(0xFFB42318))
                            if (state.summary?.courseComplete == true) StatusBadge("Course complete", Color(0xFF1E7E6F))
                        }
                        if (medication.treatmentType == TreatmentType.FIXED_COURSE) {
                            Text("Required stock: ${medication.totalRequiredStock?.stockText() ?: "0"} ${medication.doseUnit}")
                            Text("Remaining doses: ${state.summary?.remainingDoses ?: 0}")
                            if (state.summary?.insufficientStockForCourse == true) {
                                Text(
                                    "Purchase warning: stock is below the total course requirement.",
                                    color = Color(0xFFB42318),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dose history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }

            if (state.history.isEmpty()) {
                item {
                    Text(
                        "No dose history yet.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(state.history, key = { it.id }) { event ->
                    HistoryRow(event)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(event: DoseEventEntity) {
    BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(event.scheduledDateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                Text(event.scheduledDateTime.displayTime(), style = MaterialTheme.typography.bodySmall)
            }
            StatusBadge(event.status.label, statusColor(event.status))
        }
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
