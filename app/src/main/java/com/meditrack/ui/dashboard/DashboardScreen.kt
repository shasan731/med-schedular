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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.meditrack.ui.displayTime
import com.meditrack.ui.stockText
import java.time.LocalTime

@Composable
fun DashboardScreen(
    onAddMedication: () -> Unit,
    onMedicationClick: (Long) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val groups = state.doses.groupIntoTimeCards()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Today",
                subtitle = state.todayLabel,
                actionLabel = "Add Medication",
                onAction = onAddMedication
            )
        }

        if (state.stockAlerts.isNotEmpty()) {
            item {
                AlertSummaryCard(
                    alerts = state.stockAlerts,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (state.doses.isEmpty()) {
            item {
                EmptyTodayState(onAddMedication)
            }
        } else {
            items(groups, key = { it.key }) { group ->
                DoseTimeCard(
                    group = group,
                    onMedicationClick = onMedicationClick,
                    onTaken = viewModel::markTaken,
                    onSkip = viewModel::skip
                )
            }
        }
    }
}

@Composable
private fun AlertSummaryCard(
    alerts: List<StockAlert>,
    modifier: Modifier = Modifier
) {
    val hasCritical = alerts.any { it.severity == AlertSeverity.CRITICAL }
    val background = if (hasCritical) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val foreground = if (hasCritical) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = background,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (hasCritical) "Needs attention now" else "Refill reminders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = foreground
            )
            alerts.forEach { alert ->
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground
                )
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
private fun DoseTimeCard(
    group: DoseTimeGroup,
    onMedicationClick: (Long) -> Unit,
    onTaken: (Long) -> Unit,
    onSkip: (Long) -> Unit
) {
    BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${group.doses.size} medicine reminder(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = group.timeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            group.doses.forEachIndexed { index, dose ->
                if (index > 0) {
                    HorizontalDivider()
                }
                DoseRow(
                    dose = dose,
                    onMedicationClick = { onMedicationClick(dose.medicationId) },
                    onTaken = { onTaken(dose.id) },
                    onSkip = { onSkip(dose.id) }
                )
            }
        }
    }
}

@Composable
private fun DoseRow(
    dose: DoseEventWithMedication,
    onMedicationClick: () -> Unit,
    onTaken: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMedicationClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dose.medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(dose.dosageInstruction, style = MaterialTheme.typography.bodyMedium)
            }
            StatusBadge(
                text = dose.status.label,
                color = statusColor(dose.status)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dose.currentStock <= 0.0) {
                StatusBadge(text = "Out of stock", color = Color(0xFFB42318))
            } else if (dose.currentStock <= dose.doseAmount) {
                StatusBadge(text = "Low stock", color = Color(0xFFB42318))
            }
            Text(
                text = "Take ${dose.doseAmount.stockText()} ${dose.doseUnit} | Stock: ${dose.currentStock.stockText()}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 5.dp)
            )
        }

        if (dose.status == DoseStatus.PENDING || dose.status == DoseStatus.MISSED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTaken,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("Taken")
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("Skip")
                }
            }
        }
    }
}

private data class DoseTimeGroup(
    val key: String,
    val title: String,
    val timeLabel: String,
    val sortTime: LocalTime,
    val doses: List<DoseEventWithMedication>
)

private fun List<DoseEventWithMedication>.groupIntoTimeCards(): List<DoseTimeGroup> {
    return groupBy { dose -> dose.groupKey() }
        .map { (key, doses) ->
            val first = doses.minBy { it.scheduledDateTime }
            DoseTimeGroup(
                key = key,
                title = first.groupTitle(),
                timeLabel = first.scheduledDateTime.displayTime(),
                sortTime = first.scheduledDateTime.toLocalTime(),
                doses = doses.sortedWith(
                    compareBy<DoseEventWithMedication> { it.scheduledDateTime }
                        .thenBy { it.medicationName.lowercase() }
                )
            )
        }
        .sortedBy { it.sortTime }
}

private fun DoseEventWithMedication.groupKey(): String {
    val time = scheduledDateTime.toLocalTime()
    return when (time) {
        LocalTime.of(8, 0) -> "morning"
        LocalTime.of(14, 0) -> "afternoon"
        LocalTime.of(22, 0) -> "night"
        else -> time.toString()
    }
}

private fun DoseEventWithMedication.groupTitle(): String {
    val time = scheduledDateTime.toLocalTime()
    return when (time) {
        LocalTime.of(8, 0) -> "Morning"
        LocalTime.of(14, 0) -> "Noon / Afternoon"
        LocalTime.of(22, 0) -> "Night"
        else -> "Custom time"
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
