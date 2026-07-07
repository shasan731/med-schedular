package com.meditrack.ui.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.WarningBand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AddEditMedicationScreen(
    medicationId: Long?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AddEditMedicationViewModel = viewModel(
        key = "add-edit-${medicationId ?: "new"}",
        factory = AddEditMedicationViewModel.factory(medicationId)
    )
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = if (medicationId == null) "Add Medication" else "Edit Medication",
                subtitle = "Local offline medication schedule"
            )
        }
        state.errorMessage?.let { message ->
            item { WarningBand(text = message, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        state.warningMessage?.let { message ->
            item { WarningBand(text = message, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item {
            MedicationFields(
                state = state,
                update = viewModel::update
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = { viewModel.save(onSaved) }) { Text("Save") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                if (state.warningMessage != null) {
                    OutlinedButton(onClick = onSaved) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun MedicationFields(
    state: MedicationFormState,
    update: ((MedicationFormState) -> MedicationFormState) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FormTextField("Medication name", state.name) { value ->
            update { current -> current.copy(name = value) }
        }
        FormTextField("Dosage/instruction", state.dosageInstruction) {
            update { current -> current.copy(dosageInstruction = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberTextField(
                label = "Dose amount",
                value = state.doseAmount,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(doseAmount = value) } }
            FormTextField(
                label = "Dose unit",
                value = state.doseUnit,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(doseUnit = value) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberTextField(
                label = "Current stock",
                value = state.currentStock,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(currentStock = value) } }
            NumberTextField(
                label = "Low-stock days",
                value = state.lowStockThresholdDays,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(lowStockThresholdDays = value) } }
        }

        Text("Treatment type", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TreatmentType.entries.forEach { type ->
                val selected = state.treatmentType == type
                if (selected) {
                    Button(onClick = { }) { Text(type.label) }
                } else {
                    OutlinedButton(onClick = { update { it.copy(treatmentType = type) } }) {
                        Text(type.label)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateInputField(
                label = "Start date",
                value = state.startDate,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(startDate = value) } }
            DateInputField(
                label = if (state.treatmentType == TreatmentType.FIXED_COURSE) "End date" else "End date optional",
                value = state.endDate,
                modifier = Modifier.weight(1f),
                optional = state.treatmentType != TreatmentType.FIXED_COURSE
            ) { value -> update { it.copy(endDate = value) } }
        }

        Text("Schedule", style = MaterialTheme.typography.titleSmall)
        ScheduleType.entries.forEach { type ->
            val selected = state.scheduleType == type
            if (selected) {
                Button(onClick = { }) { Text(type.label) }
            } else {
                OutlinedButton(onClick = { update { it.copy(scheduleType = type) } }) {
                    Text(type.label)
                }
            }
        }

        if (state.scheduleType == ScheduleType.SPECIFIC_TIMES) {
            Text("Quick reminder choices", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { update { it.copy(reminderTimes = "08:00") } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Morning only")
            }
            OutlinedButton(
                onClick = { update { it.copy(reminderTimes = "22:00") } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Night only")
            }
            Button(
                onClick = { update { it.copy(reminderTimes = "08:00, 14:00, 22:00") } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use Morning, Afternoon, and Night")
            }
            FormTextField(
                label = "Specific reminder times",
                value = state.reminderTimes,
                supporting = "You can also type times separated by commas, e.g. 08:00, 2:00 PM, 10:00 PM."
            ) { value -> update { it.copy(reminderTimes = value) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberTextField(
                    label = "Interval value",
                    value = state.intervalValue,
                    modifier = Modifier.weight(1f)
                ) { value -> update { it.copy(intervalValue = value) } }
                FormTextField(
                    label = "Reminder time",
                    value = state.reminderTimes,
                    modifier = Modifier.weight(1f)
                ) { value -> update { it.copy(reminderTimes = value) } }
            }
            if (state.scheduleType == ScheduleType.WEEKLY_INTERVAL) {
                FormTextField(
                    label = "Days of week",
                    value = state.daysOfWeek,
                    supporting = "1=Mon through 7=Sun, comma-separated"
                ) { value -> update { it.copy(daysOfWeek = value) } }
            }
            if (state.scheduleType == ScheduleType.MONTHLY_INTERVAL) {
                NumberTextField(
                    label = "Day of month",
                    value = state.dayOfMonth
                ) { value -> update { it.copy(dayOfMonth = value) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateInputField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showPicker = true }) {
                Text("Pick")
            }
            if (optional && value.isNotBlank()) {
                TextButton(onClick = { onValueChange("") }) {
                    Text("Clear")
                }
            }
        }
    }

    if (showPicker) {
        val initialMillis = remember(value) {
            value.toLocalDateOrNull()?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun String.toLocalDateOrNull(): LocalDate? {
    return runCatching { LocalDate.parse(trim()) }.getOrNull()
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = supporting == null
    )
}

@Composable
private fun NumberTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
