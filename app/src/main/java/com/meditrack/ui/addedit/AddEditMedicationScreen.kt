package com.meditrack.ui.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.PrimaryActionButton
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.SecondaryActionButton
import com.meditrack.ui.components.WarningBand
import com.meditrack.ui.stockText
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

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = if (medicationId == null) "Add Medication" else "Edit Medication",
                    subtitle = "Use the same pattern your doctor writes, such as 1+0+1."
                )
            }
            state.errorMessage?.let { message ->
                item { WarningBand(text = message, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            state.warningMessage?.let { message ->
                item { WarningBand(text = message, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            item {
                MedicineSection(state = state, update = viewModel::update)
            }
            item {
                TreatmentSection(state = state, update = viewModel::update)
            }
            item {
                PrescriptionSection(state = state, update = viewModel::update)
            }
            item {
                StockSection(state = state, update = viewModel::update)
            }
        }
        AddEditBottomActions(
            savedWithWarning = state.warningMessage != null,
            onSave = { viewModel.save(onSaved) },
            onDone = onSaved,
            onCancel = onCancel
        )
    }
}

@Composable
private fun AddEditBottomActions(
    savedWithWarning: Boolean,
    onSave: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
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
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            PrimaryActionButton(
                text = if (savedWithWarning) "Done" else "Save Medication",
                onClick = if (savedWithWarning) onDone else onSave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MedicineSection(
    state: MedicationFormState,
    update: ((MedicationFormState) -> MedicationFormState) -> Unit
) {
    BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("1. Medicine")
            FormTextField("Medicine name", state.name) { value ->
                update { it.copy(name = value) }
            }
            FormTextField(
                label = "Instruction, for example after meal",
                value = state.dosageInstruction,
                supporting = "Optional. If empty, MediTrack creates a simple instruction from the dose pattern."
            ) { value -> update { it.copy(dosageInstruction = value) } }
            FormTextField(
                label = "Medicine unit",
                value = state.doseUnit,
                supporting = "Examples: tablet, capsule, ml, drop"
            ) { value -> update { it.copy(doseUnit = value) } }
        }
    }
}

@Composable
private fun TreatmentSection(
    state: MedicationFormState,
    update: ((MedicationFormState) -> MedicationFormState) -> Unit
) {
    BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("2. Treatment length")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TreatmentButton(
                    label = "Continuous",
                    selected = state.treatmentType == TreatmentType.CONTINUOUS,
                    modifier = Modifier.weight(1f)
                ) {
                    update { it.copy(treatmentType = TreatmentType.CONTINUOUS, endDate = "") }
                }
                TreatmentButton(
                    label = "Fixed Course",
                    selected = state.treatmentType == TreatmentType.FIXED_COURSE,
                    modifier = Modifier.weight(1f)
                ) {
                    update { it.copy(treatmentType = TreatmentType.FIXED_COURSE) }
                }
            }
            DateInputField(
                label = "Start date",
                value = state.startDate,
                modifier = Modifier.fillMaxWidth()
            ) { value -> update { it.copy(startDate = value) } }

            if (state.treatmentType == TreatmentType.FIXED_COURSE) {
                Text(
                    "Course length",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberTextField(
                        label = "How many",
                        value = state.courseDurationValue,
                        modifier = Modifier.weight(1f)
                    ) { value -> update { it.copy(courseDurationValue = value) } }
                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CourseDurationUnit.entries.forEach { unit ->
                            TreatmentButton(
                                label = unit.label,
                                selected = state.courseDurationUnit == unit,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                update { it.copy(courseDurationUnit = unit) }
                            }
                        }
                    }
                }
                Text(
                    text = "End date will be ${state.autoEndDate() ?: "calculated after duration is entered"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                DateInputField(
                    label = "End date optional",
                    value = state.endDate,
                    modifier = Modifier.fillMaxWidth(),
                    optional = true
                ) { value -> update { it.copy(endDate = value) } }
            }
        }
    }
}

@Composable
private fun PrescriptionSection(
    state: MedicationFormState,
    update: ((MedicationFormState) -> MedicationFormState) -> Unit
) {
    BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("3. Dose schedule")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Advanced schedule", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Use only for hourly, weekly, or monthly plans.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Switch(
                    checked = state.useAdvancedSchedule,
                    onCheckedChange = { checked -> update { it.copy(useAdvancedSchedule = checked) } }
                )
            }

            if (state.useAdvancedSchedule) {
                AdvancedScheduleFields(state = state, update = update)
            } else {
                SimplePrescriptionFields(state = state, update = update)
            }
        }
    }
}

@Composable
private fun SimplePrescriptionFields(
    state: MedicationFormState,
    update: ((MedicationFormState) -> MedicationFormState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Enter the pattern like a prescription. Example: 1+0+1 means Morning 1, Afternoon 0, Night 1.",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberTextField(
                label = "Morning",
                value = state.morningDose,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(morningDose = value) } }
            NumberTextField(
                label = "Afternoon",
                value = state.afternoonDose,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(afternoonDose = value) } }
            NumberTextField(
                label = "Night",
                value = state.nightDose,
                modifier = Modifier.weight(1f)
            ) { value -> update { it.copy(nightDose = value) } }
        }
        Text(
            text = "Pattern: ${state.prescriptionPattern()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { update { it.copy(morningDose = "1", afternoonDose = "0", nightDose = "1") } },
                modifier = Modifier.weight(1f)
            ) {
                Text("1+0+1")
            }
            OutlinedButton(
                onClick = { update { it.copy(morningDose = "1", afternoonDose = "1", nightDose = "1") } },
                modifier = Modifier.weight(1f)
            ) {
                Text("1+1+1")
            }
            OutlinedButton(
                onClick = { update { it.copy(morningDose = "0", afternoonDose = "0", nightDose = "1") } },
                modifier = Modifier.weight(1f)
            ) {
                Text("0+0+1")
            }
        }
        Text(
            "Reminder times: Morning 8:00 AM, Afternoon 2:00 PM, Night 10:00 PM.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        state.estimatedSimpleRequiredStock()?.let { required ->
            Text(
                "Full course needs about ${required.stockText()} ${state.doseUnit.ifBlank { "units" }}.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AdvancedScheduleFields(
    state: MedicationFormState,
    update: ((MedicationFormState) -> MedicationFormState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberTextField(
            label = "Dose amount each time",
            value = state.doseAmount
        ) { value -> update { it.copy(doseAmount = value) } }
        Text("Schedule type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ScheduleType.entries.forEach { type ->
            TreatmentButton(
                label = type.label,
                selected = state.scheduleType == type,
                modifier = Modifier.fillMaxWidth()
            ) {
                update { it.copy(scheduleType = type) }
            }
        }

        if (state.scheduleType == ScheduleType.SPECIFIC_TIMES) {
            FormTextField(
                label = "Reminder times",
                value = state.reminderTimes,
                supporting = "Separate times with commas, e.g. 08:00, 2:00 PM, 10:00 PM."
            ) { value -> update { it.copy(reminderTimes = value) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberTextField(
                    label = "Every",
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
                    supporting = "1=Mon through 7=Sun, comma-separated."
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

@Composable
private fun StockSection(
    state: MedicationFormState,
    update: ((MedicationFormState) -> MedicationFormState) -> Unit
) {
    BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("4. Stock and refill reminder")
            NumberTextField(
                label = "Current stock",
                value = state.currentStock,
                supporting = "How many ${state.doseUnit.ifBlank { "units" }} you have now."
            ) { value -> update { it.copy(currentStock = value) } }
            NumberTextField(
                label = "Warn me when stock is this many days left",
                value = state.lowStockThresholdDays
            ) { value -> update { it.copy(lowStockThresholdDays = value) } }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun TreatmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(48.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) {
            Text(label)
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
                Text("Pick date")
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
    supporting: String? = null,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
