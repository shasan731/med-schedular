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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.R
import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.FoodRelation
import com.meditrack.domain.model.TreatmentType
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.RefillDialog
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.StatusBadge
import com.meditrack.ui.daysRemainingText
import com.meditrack.ui.displayTime
import com.meditrack.ui.labelRes
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
    val context = LocalContext.current
    var showRefill by remember { mutableStateOf(false) }

    if (showRefill && medication != null) {
        RefillDialog(
            medicationName = medication.name,
            unit = medication.doseUnit,
            onDismiss = { showRefill = false },
            onConfirm = { added ->
                viewModel.refill(added)
                showRefill = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = medication?.name ?: stringResource(R.string.detail_default_title),
                subtitle = medication?.dosageInstruction,
                actionLabel = if (medication != null) stringResource(R.string.action_edit) else null,
                onAction = medication?.let { { onEdit(it.id) } },
                onBack = onBack
            )
        }
        if (medication == null) {
            item {
                BasicCard(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.detail_not_found))
                        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
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
                        Text(
                            stringResource(R.string.detail_stock, medication.currentStock.stockText(), medication.doseUnit),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(
                                R.string.detail_days_remaining,
                                state.summary?.daysRemaining?.daysRemainingText(context)
                                    ?: stringResource(R.string.detail_unknown)
                            )
                        )
                        Text(stringResource(R.string.detail_treatment, stringResource(medication.treatmentType.labelRes())))
                        Text(stringResource(R.string.detail_schedule, state.scheduleSummary))
                        if (medication.foodRelation != FoodRelation.NONE) {
                            Text(stringResource(R.string.detail_food, stringResource(medication.foodRelation.labelRes())))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.summary?.outOfStock == true) StatusBadge(stringResource(R.string.badge_out_of_stock), Color(0xFFB42318))
                            if (state.summary?.lowStock == true) StatusBadge(stringResource(R.string.badge_low_stock), Color(0xFFB54708))
                            if (state.summary?.courseComplete == true) StatusBadge(stringResource(R.string.badge_course_complete), Color(0xFF1E7E6F))
                        }
                        if (medication.treatmentType == TreatmentType.FIXED_COURSE) {
                            Text(stringResource(R.string.detail_required_stock, medication.totalRequiredStock?.stockText() ?: "0", medication.doseUnit))
                            Text(stringResource(R.string.detail_remaining_doses, state.summary?.remainingDoses ?: 0))
                            if (state.summary?.insufficientStockForCourse == true) {
                                Text(
                                    stringResource(R.string.detail_purchase_warning),
                                    color = Color(0xFFB42318),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = { showRefill = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_refill_stock))
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.dose_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (state.history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_dose_history),
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
            StatusBadge(stringResource(event.status.labelRes()), statusColor(event.status))
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
