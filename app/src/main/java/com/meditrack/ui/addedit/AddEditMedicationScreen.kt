package com.meditrack.ui.addedit

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.meditrack.ui.components.DoseStepperRow
import com.meditrack.ui.components.PrimaryActionButton
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.SecondaryActionButton
import com.meditrack.ui.components.WarningBand
import com.meditrack.ui.longDisplayDate
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
                    title = if (medicationId == null) "Add Medicine" else "Edit Medicine",
                    subtitle = "Just answer a few simple questions. You can change anything later."
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
                text = if (savedWithWarning) "Done" else "Save",
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
            SectionTitle("1. What is the medicine?")
            FormTextField("Medicine name", state.name) { value ->
                update { it.copy(name = value) }
            }
            FormTextField(
                label = "What form is it? (tablet, capsule, ml, drop)",
                value = state.doseUnit,
                supporting = "This is what we count. Most people leave it as tablet."
            ) { value -> update { it.copy(doseUnit = value) } }
            FormTextField(
                label = "Special note (optional)",
                value = state.dosageInstruction,
                supporting = "For example: after meal. Leave empty if you are not sure."
            ) { value -> update { it.copy(dosageInstruction = value) } }
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
            SectionTitle("2. How long will you take it?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TreatmentButton(
                    label = "Every day, ongoing",
                    selected = state.treatmentType == TreatmentType.CONTINUOUS,
                    modifier = Modifier.weight(1f)
                ) {
                    update { it.copy(treatmentType = TreatmentType.CONTINUOUS, endDate = "") }
                }
                TreatmentButton(
                    label = "For a set number of days",
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
                    "How many days is the course?",
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
                val friendlyEndDate = state.autoEndDate()
                    ?.let { runCatching { LocalDate.parse(it).longDisplayDate() }.getOrNull() }
                Text(
                    text = if (friendlyEndDate != null) {
                        "Last day: $friendlyEndDate."
                    } else {
                        "The last day is worked out once you enter the number of days."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                DateInputField(
                    label = "End date (optional)",
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
            SectionTitle("3. How much and when?")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Set a custom schedule", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Most people can leave this off. Turn it on only for hourly, weekly, or monthly plans.",
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "How many do you take at each time of day? Tap + or − to set the amount.",
            style = MaterialTheme.typography.bodyMedium
        )
        DoseStepperRow(
            label = "Morning",
            sublabel = "Reminder at 8:00 AM",
            value = state.morningDose
        ) { value -> update { it.copy(morningDose = value) } }
        DoseStepperRow(
            label = "Afternoon",
            sublabel = "Reminder at 2:00 PM",
            value = state.afternoonDose
        ) { value -> update { it.copy(afternoonDose = value) } }
        DoseStepperRow(
            label = "Night",
            sublabel = "Reminder at 10:00 PM",
            value = state.nightDose
        ) { value -> update { it.copy(nightDose = value) } }

        Text(
            "Quick fill",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { update { it.copy(morningDose = "1", afternoonDose = "0", nightDose = "1") } },
                modifier = Modifier.weight(1f)
            ) {
                Text("Morning + night")
            }
            OutlinedButton(
                onClick = { update { it.copy(morningDose = "0", afternoonDose = "0", nightDose = "1") } },
                modifier = Modifier.weight(1f)
            ) {
                Text("Night only")
            }
        }
        state.estimatedSimpleRequiredStock()?.let { required ->
            Text(
                "You will need about ${required.stockText()} ${state.doseUnit.ifBlank { "units" }} for the whole course.",
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
        DoseStepperRow(
            label = "How many each time",
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
            SectionTitle("4. How many do you have?")
            NumberTextField(
                label = "Amount you have now",
                value = state.currentStock,
                supporting = "The number of ${state.doseUnit.ifBlank { "units" }} in your box or bottle right now."
            ) { value -> update { it.copy(currentStock = value) } }
            NumberTextField(
                label = "Warn me when about this many days are left",
                value = state.lowStockThresholdDays,
                supporting = "We will remind you to refill before you run out."
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
    val friendlyDate = value.toLocalDateOrNull()?.longDisplayDate()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(friendlyDate ?: "Tap to choose a date", fontWeight = FontWeight.SemiBold)
        }
        if (optional && value.isNotBlank()) {
            TextButton(onClick = { onValueChange("") }) {
                Text("Clear date")
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
