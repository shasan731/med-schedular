package com.meditrack.ui.vaccination

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.R
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.PrimaryActionButton
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.SecondaryActionButton
import com.meditrack.ui.components.WarningBand
import com.meditrack.ui.longDisplayDate
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AddEditVaccinationScreen(
    vaccinationId: Long?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AddEditVaccinationViewModel = viewModel(
        key = "add-edit-vaccination-${vaccinationId ?: "new"}",
        factory = AddEditVaccinationViewModel.factory(vaccinationId)
    )
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = stringResource(
                        if (vaccinationId == null) R.string.vacc_add_title else R.string.vacc_edit_title
                    ),
                    subtitle = stringResource(R.string.vacc_add_subtitle)
                )
            }
            state.errorMessage?.let { message ->
                item { WarningBand(text = message, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            item {
                BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                            label = { Text(stringResource(R.string.vacc_field_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.doseLabel,
                            onValueChange = { value -> viewModel.update { it.copy(doseLabel = value) } },
                            label = { Text(stringResource(R.string.vacc_field_dose_label)) },
                            supportingText = { Text(stringResource(R.string.vacc_field_dose_label_help)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DateField(
                            value = state.date,
                            onValueChange = { value -> viewModel.update { it.copy(date = value) } }
                        )
                        OutlinedTextField(
                            value = state.time,
                            onValueChange = { value -> viewModel.update { it.copy(time = value) } },
                            label = { Text(stringResource(R.string.vacc_field_time)) },
                            supportingText = { Text(stringResource(R.string.vacc_field_time_help)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.note,
                            onValueChange = { value -> viewModel.update { it.copy(note = value) } },
                            label = { Text(stringResource(R.string.vacc_field_note)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryActionButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
                PrimaryActionButton(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.save(onSaved) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val friendlyDate = runCatching { LocalDate.parse(value.trim()) }.getOrNull()?.longDisplayDate()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.vacc_field_date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(friendlyDate ?: stringResource(R.string.date_choose), fontWeight = FontWeight.SemiBold)
        }
    }

    if (showPicker) {
        val initialMillis = remember(value) {
            runCatching { LocalDate.parse(value.trim()) }.getOrNull()
                ?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onValueChange(selectedDate.toString())
                        }
                        showPicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
