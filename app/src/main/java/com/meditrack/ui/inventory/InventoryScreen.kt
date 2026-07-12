package com.meditrack.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.R
import com.meditrack.domain.model.TreatmentType
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.ConfirmingTextButton
import com.meditrack.ui.components.RefillDialog
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.StatusBadge
import com.meditrack.ui.dangerColor
import com.meditrack.ui.daysRemainingText
import com.meditrack.ui.infoColor
import com.meditrack.ui.labelRes
import com.meditrack.ui.stockText
import com.meditrack.ui.successColor
import com.meditrack.ui.warningColor

@Composable
fun InventoryScreen(
    onAddMedication: () -> Unit,
    onEditMedication: (Long) -> Unit,
    onMedicationClick: (Long) -> Unit,
    viewModel: InventoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var pendingDisableId by remember { mutableStateOf<Long?>(null) }
    var refillTarget by remember { mutableStateOf<InventoryItemUi?>(null) }

    refillTarget?.let { target ->
        RefillDialog(
            medicationName = target.medication.name,
            unit = target.medication.doseUnit,
            onDismiss = { refillTarget = null },
            onConfirm = { added ->
                viewModel.refill(target.medication.id, added)
                refillTarget = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.medicines_title),
                subtitle = stringResource(R.string.medicines_count, state.items.size)
            )
        }

        if (state.items.isEmpty()) {
            item {
                BasicCard(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.medicines_empty_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.medicines_empty_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAddMedication) { Text(stringResource(R.string.action_add_medicine_short)) }
                    }
                }
            }
        } else {
            items(state.items, key = { it.medication.id }) { item ->
                InventoryCard(
                    item = item,
                    awaitingDelete = pendingDeleteId == item.medication.id,
                    awaitingDisable = pendingDisableId == item.medication.id,
                    onClick = { onMedicationClick(item.medication.id) },
                    onRefill = { refillTarget = item },
                    onEdit = { onEditMedication(item.medication.id) },
                    onDisableFirstClick = { pendingDisableId = item.medication.id },
                    onDisableConfirm = {
                        viewModel.disableMedication(item.medication.id)
                        pendingDisableId = null
                    },
                    onReactivate = { viewModel.reactivateMedication(item.medication.id) },
                    onDeleteFirstClick = { pendingDeleteId = item.medication.id },
                    onDeleteConfirm = {
                        viewModel.deleteMedication(item.medication.id)
                        pendingDeleteId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun InventoryCard(
    item: InventoryItemUi,
    awaitingDelete: Boolean,
    awaitingDisable: Boolean,
    onClick: () -> Unit,
    onRefill: () -> Unit,
    onEdit: () -> Unit,
    onDisableFirstClick: () -> Unit,
    onDisableConfirm: () -> Unit,
    onReactivate: () -> Unit,
    onDeleteFirstClick: () -> Unit,
    onDeleteConfirm: () -> Unit
) {
    val medication = item.medication
    val context = LocalContext.current
    BasicCard(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(medication.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(medication.dosageInstruction, style = MaterialTheme.typography.bodyMedium)
                    Text(item.scheduleSummary, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${medication.currentStock.stockText()} ${medication.doseUnit}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(item.summary.daysRemaining.daysRemainingText(context), style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.open_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!medication.isActive) {
                    StatusBadge(stringResource(R.string.badge_disabled), infoColor())
                } else {
                    StatusBadge(stringResource(medication.treatmentType.labelRes()), infoColor())
                    if (item.summary.outOfStock) {
                        StatusBadge(stringResource(R.string.badge_out_of_stock), dangerColor())
                    } else if (item.summary.lowStock) {
                        StatusBadge(stringResource(R.string.badge_low_stock), warningColor())
                    }
                    if (item.summary.courseComplete) {
                        StatusBadge(stringResource(R.string.badge_course_complete), successColor())
                    }
                }
            }

            if (medication.treatmentType == TreatmentType.FIXED_COURSE) {
                Text(
                    text = stringResource(
                        R.string.fixed_course_summary,
                        medication.totalRequiredStock?.stockText() ?: "0",
                        medication.doseUnit,
                        item.summary.remainingDoses ?: 0
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                if (medication.isActive && item.summary.insufficientStockForCourse) {
                    Text(
                        stringResource(R.string.purchase_warning),
                        color = dangerColor(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (medication.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRefill,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_refill))
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_edit))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (medication.isActive) {
                    ConfirmingTextButton(
                        label = stringResource(R.string.action_disable),
                        confirmingLabel = stringResource(R.string.action_disable_confirm),
                        awaitingConfirmation = awaitingDisable,
                        onFirstClick = onDisableFirstClick,
                        onConfirm = onDisableConfirm,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Button(onClick = onReactivate, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_reactivate))
                    }
                }
                ConfirmingTextButton(
                    label = stringResource(R.string.action_delete),
                    confirmingLabel = stringResource(R.string.action_delete_confirm),
                    awaitingConfirmation = awaitingDelete,
                    onFirstClick = onDeleteFirstClick,
                    onConfirm = onDeleteConfirm,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
