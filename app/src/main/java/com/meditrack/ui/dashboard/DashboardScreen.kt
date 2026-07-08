package com.meditrack.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.R
import com.meditrack.data.local.entity.DoseEventWithMedication
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.FoodRelation
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.StatusBadge
import com.meditrack.ui.displayTime
import com.meditrack.ui.labelRes
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

    // Refresh whenever the screen resumes so the timeline rolls over to a new day, overdue doses
    // become missed, and reminders are rescheduled even if the process was kept alive in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshToday()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.today_title),
                subtitle = state.todayLabel
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
                    stockStatus = state.stockStatus,
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
                text = stringResource(
                    if (hasCritical) R.string.alert_needs_attention else R.string.alert_refill_reminders
                ),
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
            Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.today_empty_body))
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddMedication) { Text(stringResource(R.string.action_add_medicine_short)) }
        }
    }
}

@Composable
private fun DoseTimeCard(
    group: DoseTimeGroup,
    stockStatus: Map<Long, StockStatus>,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(group.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = group.timeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            group.doses.forEachIndexed { index, dose ->
                if (index > 0) {
                    HorizontalDivider()
                }
                DoseRow(
                    dose = dose,
                    stockStatus = stockStatus[dose.medicationId] ?: StockStatus.OK,
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
    stockStatus: StockStatus,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dose.medicationName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            // Only badge doses the user has acted on; "Pending" is the expected default and adds noise.
            if (dose.status != DoseStatus.PENDING) {
                StatusBadge(
                    text = stringResource(dose.status.labelRes()),
                    color = statusColor(dose.status)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dose_take_amount, dose.doseAmount.stockText(), dose.doseUnit),
                style = MaterialTheme.typography.titleMedium
            )
            when (stockStatus) {
                StockStatus.OUT -> StatusBadge(text = stringResource(R.string.badge_out_of_stock), color = Color(0xFFB42318))
                StockStatus.LOW -> StatusBadge(text = stringResource(R.string.badge_low_stock), color = Color(0xFFB54708))
                StockStatus.OK -> Unit
            }
        }

        if (dose.foodRelation != FoodRelation.NONE) {
            Text(
                text = stringResource(dose.foodRelation.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
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
                    Text(stringResource(R.string.action_taken))
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(stringResource(R.string.action_skip))
                }
            }
        }
    }
}

private data class DoseTimeGroup(
    val key: String,
    @StringRes val titleRes: Int,
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
                titleRes = first.groupTitleRes(),
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

@StringRes
private fun DoseEventWithMedication.groupTitleRes(): Int {
    val time = scheduledDateTime.toLocalTime()
    return when (time) {
        LocalTime.of(8, 0) -> R.string.group_morning
        LocalTime.of(14, 0) -> R.string.group_afternoon
        LocalTime.of(22, 0) -> R.string.group_night
        else -> R.string.group_custom
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
