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
import com.meditrack.domain.model.FoodRelation
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.DoseStepperRow
import com.meditrack.ui.components.PrimaryActionButton
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.SecondaryActionButton
import com.meditrack.ui.components.WarningBand
import com.meditrack.ui.longDisplayDate
import com.meditrack.ui.labelRes
import com.meditrack.ui.stockText
import androidx.compose.ui.res.stringResource
import com.meditrack.R
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
                    title = stringResource(if (medicationId == null) R.string.add_title else R.string.edit_title),
                    subtitle = stringResource(R.string.add_subtitle)
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
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            PrimaryActionButton(
                text = stringResource(if (savedWithWarning) R.string.action_done else R.string.action_save),
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
            SectionTitle(stringResource(R.string.section_medicine))
            FormTextField(stringResource(R.string.field_medicine_name), state.name) { value ->
                update { it.copy(name = value) }
            }
            FormTextField(
                label = stringResource(R.string.field_form),
                value = state.doseUnit,
                supporting = stringResource(R.string.field_form_help)
            ) { value -> update { it.copy(doseUnit = value) } }
            FormTextField(
                label = stringResource(R.string.field_note),
                value = state.dosageInstruction,
                supporting = stringResource(R.string.field_note_help)
            ) { value -> update { it.copy(dosageInstruction = value) } }

            Text(
                stringResource(R.string.food_section_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            val foodOptions = FoodRelation.entries
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                foodOptions.chunked(2).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOptions.forEach { option ->
                            TreatmentButton(
                                label = stringResource(option.labelRes()),
                                selected = state.foodRelation == option,
                                modifier = Modifier.weight(1f)
                            ) {
                                update { it.copy(foodRelation = option) }
                            }
                        }
                    }
                }
            }
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
            SectionTitle(stringResource(R.string.section_duration))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TreatmentButton(
                    label = stringResource(R.string.treatment_continuous),
                    selected = state.treatmentType == TreatmentType.CONTINUOUS,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    update { it.copy(treatmentType = TreatmentType.CONTINUOUS, endDate = "") }
                }
                TreatmentButton(
                    label = stringResource(R.string.treatment_fixed),
                    selected = state.treatmentType == TreatmentType.FIXED_COURSE,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    update { it.copy(treatmentType = TreatmentType.FIXED_COURSE) }
                }
            }
            DateInputField(
                label = stringResource(R.string.field_start_date),
                value = state.startDate,
                modifier = Modifier.fillMaxWidth()
            ) { value -> update { it.copy(startDate = value) } }

            if (state.treatmentType == TreatmentType.FIXED_COURSE) {
                Text(
                    stringResource(R.string.course_length_q),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberTextField(
                        label = stringResource(R.string.field_how_many),
                        value = state.courseDurationValue,
                        modifier = Modifier.weight(1f)
                    ) { value -> update { it.copy(courseDurationValue = value) } }
                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CourseDurationUnit.entries.forEach { unit ->
                            TreatmentButton(
                                label = stringResource(unit.labelRes()),
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
                        stringResource(R.string.course_last_day, friendlyEndDate)
                    } else {
                        stringResource(R.string.course_last_day_pending)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                DateInputField(
                    label = stringResource(R.string.field_end_date_optional),
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
            SectionTitle(stringResource(R.string.section_schedule))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.advanced_toggle_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.advanced_toggle_help),
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
            stringResource(R.string.simple_help),
            style = MaterialTheme.typography.bodyMedium
        )
        DoseStepperRow(
            label = stringResource(R.string.group_morning),
            sublabel = stringResource(R.string.sub_morning),
            value = state.morningDose
        ) { value -> update { it.copy(morningDose = value) } }
        DoseStepperRow(
            label = stringResource(R.string.group_afternoon),
            sublabel = stringResource(R.string.sub_afternoon),
            value = state.afternoonDose
        ) { value -> update { it.copy(afternoonDose = value) } }
        DoseStepperRow(
            label = stringResource(R.string.group_night),
            sublabel = stringResource(R.string.sub_night),
            value = state.nightDose
        ) { value -> update { it.copy(nightDose = value) } }

        Text(
            stringResource(R.string.quick_fill),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { update { it.copy(morningDose = "1", afternoonDose = "0", nightDose = "1") } },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.preset_morning_night))
            }
            OutlinedButton(
                onClick = { update { it.copy(morningDose = "0", afternoonDose = "0", nightDose = "1") } },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.preset_night_only))
            }
        }
        state.estimatedSimpleRequiredStock()?.let { required ->
            Text(
                stringResource(
                    R.string.course_needs,
                    required.stockText(),
                    state.doseUnit.ifBlank { stringResource(R.string.units_fallback) }
                ),
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
            label = stringResource(R.string.adv_dose_each),
            value = state.doseAmount
        ) { value -> update { it.copy(doseAmount = value) } }
        Text(stringResource(R.string.adv_schedule_type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ScheduleType.entries.forEach { type ->
            TreatmentButton(
                label = stringResource(type.labelRes()),
                selected = state.scheduleType == type,
                modifier = Modifier.fillMaxWidth()
            ) {
                update { it.copy(scheduleType = type) }
            }
        }

        if (state.scheduleType == ScheduleType.SPECIFIC_TIMES) {
            FormTextField(
                label = stringResource(R.string.adv_reminder_times),
                value = state.reminderTimes,
                supporting = stringResource(R.string.adv_reminder_times_help)
            ) { value -> update { it.copy(reminderTimes = value) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberTextField(
                    label = stringResource(R.string.adv_every),
                    value = state.intervalValue,
                    modifier = Modifier.weight(1f)
                ) { value -> update { it.copy(intervalValue = value) } }
                FormTextField(
                    label = stringResource(R.string.adv_reminder_time),
                    value = state.reminderTimes,
                    modifier = Modifier.weight(1f)
                ) { value -> update { it.copy(reminderTimes = value) } }
            }
            if (state.scheduleType == ScheduleType.WEEKLY_INTERVAL) {
                FormTextField(
                    label = stringResource(R.string.adv_days_of_week),
                    value = state.daysOfWeek,
                    supporting = stringResource(R.string.adv_days_of_week_help)
                ) { value -> update { it.copy(daysOfWeek = value) } }
            }
            if (state.scheduleType == ScheduleType.MONTHLY_INTERVAL) {
                NumberTextField(
                    label = stringResource(R.string.adv_day_of_month),
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
            SectionTitle(stringResource(R.string.section_stock))
            NumberTextField(
                label = stringResource(R.string.field_current_stock),
                value = state.currentStock,
                supporting = stringResource(
                    R.string.field_current_stock_help,
                    state.doseUnit.ifBlank { stringResource(R.string.units_fallback) }
                )
            ) { value -> update { it.copy(currentStock = value) } }
            NumberTextField(
                label = stringResource(R.string.field_low_stock),
                value = state.lowStockThresholdDays,
                supporting = stringResource(R.string.field_low_stock_help)
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
            Text(friendlyDate ?: stringResource(R.string.date_choose), fontWeight = FontWeight.SemiBold)
        }
        if (optional && value.isNotBlank()) {
            TextButton(onClick = { onValueChange("") }) {
                Text(stringResource(R.string.clear_date))
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
