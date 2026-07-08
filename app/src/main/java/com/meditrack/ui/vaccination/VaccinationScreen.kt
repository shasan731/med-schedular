package com.meditrack.ui.vaccination

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.R
import com.meditrack.data.local.entity.VaccinationEntity
import com.meditrack.domain.model.VaccinationStatus
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.ConfirmingTextButton
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.StatusBadge
import com.meditrack.ui.displayTime
import com.meditrack.ui.labelRes
import com.meditrack.ui.longDisplayDate

@Composable
fun VaccinationScreen(
    onAddVaccination: () -> Unit,
    onEditVaccination: (Long) -> Unit,
    viewModel: VaccinationViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.vacc_title),
                subtitle = stringResource(R.string.vacc_subtitle),
                actionLabel = stringResource(R.string.vacc_add),
                onAction = onAddVaccination
            )
        }

        if (state.items.isEmpty()) {
            item {
                BasicCard(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.vacc_empty_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.vacc_empty_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAddVaccination) { Text(stringResource(R.string.vacc_add)) }
                    }
                }
            }
        } else {
            items(state.items, key = { it.id }) { vaccination ->
                VaccinationCard(
                    vaccination = vaccination,
                    awaitingDelete = pendingDeleteId == vaccination.id,
                    onMarkDone = { viewModel.markDone(vaccination.id) },
                    onMarkUpcoming = { viewModel.markUpcoming(vaccination.id) },
                    onEdit = { onEditVaccination(vaccination.id) },
                    onDeleteFirstClick = { pendingDeleteId = vaccination.id },
                    onDeleteConfirm = {
                        viewModel.delete(vaccination.id)
                        pendingDeleteId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun VaccinationCard(
    vaccination: VaccinationEntity,
    awaitingDelete: Boolean,
    onMarkDone: () -> Unit,
    onMarkUpcoming: () -> Unit,
    onEdit: () -> Unit,
    onDeleteFirstClick: () -> Unit,
    onDeleteConfirm: () -> Unit
) {
    BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vaccination.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (vaccination.doseLabel.isNotBlank()) {
                        Text(vaccination.doseLabel, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "${vaccination.scheduledDateTime.toLocalDate().longDisplayDate()} · ${vaccination.scheduledDateTime.displayTime()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    vaccination.note?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                StatusBadge(
                    text = stringResource(vaccination.status.labelRes()),
                    color = statusColor(vaccination.status)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vaccination.status == VaccinationStatus.DONE) {
                    OutlinedButton(onClick = onMarkUpcoming, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.vacc_mark_not_done))
                    }
                } else {
                    Button(onClick = onMarkDone, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.vacc_mark_done))
                    }
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_edit))
                }
            }
            ConfirmingTextButton(
                label = stringResource(R.string.action_delete),
                confirmingLabel = stringResource(R.string.action_delete_confirm),
                awaitingConfirmation = awaitingDelete,
                onFirstClick = onDeleteFirstClick,
                onConfirm = onDeleteConfirm,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun statusColor(status: VaccinationStatus): Color {
    return when (status) {
        VaccinationStatus.UPCOMING -> Color(0xFF355C7D)
        VaccinationStatus.DONE -> Color(0xFF1E7E6F)
        VaccinationStatus.MISSED -> Color(0xFFB42318)
    }
}
