package com.meditrack.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.ConfirmingTextButton
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.WarningBand
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val exportJson by viewModel.exportJson.collectAsState()
    val message by viewModel.message.collectAsState()
    var threshold by remember { mutableStateOf(settings.defaultLowStockThresholdDays.toString()) }
    var confirmingClear by remember { mutableStateOf(false) }
    var pendingFileExport by remember { mutableStateOf(false) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = exportJson
        if (uri == null || json == null) {
            pendingFileExport = false
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open the selected file.")
        }.onSuccess {
            viewModel.setMessage("Local data exported to the selected JSON file.")
        }.onFailure { error ->
            viewModel.setMessage("Export failed: ${error.message ?: "unknown error"}")
        }
        pendingFileExport = false
    }

    LaunchedEffect(settings.defaultLowStockThresholdDays) {
        threshold = settings.defaultLowStockThresholdDays.toString()
    }

    LaunchedEffect(exportJson, pendingFileExport) {
        if (pendingFileExport && exportJson != null) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            exportFileLauncher.launch("meditrack-export-$timestamp.json")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = "Settings",
                subtitle = "Local-only preferences"
            )
        }
        message?.let {
            item { WarningBand(text = it, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item {
            BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Defaults", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it },
                            label = { Text("Default low-stock threshold days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = { viewModel.saveLowStockThreshold(threshold) }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
        item {
            BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    SettingSwitchRow(
                        label = "Dose reminders enabled",
                        checked = settings.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled
                    )
                    SettingSwitchRow(
                        label = "Vibration enabled",
                        checked = settings.vibrationEnabled,
                        onCheckedChange = viewModel::setVibrationEnabled
                    )
                }
            }
        }
        item {
            BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Local data", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                pendingFileExport = true
                                viewModel.exportJson()
                            }
                        ) {
                            Text("Export JSON")
                        }
                        ConfirmingTextButton(
                            label = "Clear all data",
                            confirmingLabel = "Confirm clear",
                            awaitingConfirmation = confirmingClear,
                            onFirstClick = { confirmingClear = true },
                            onConfirm = {
                                viewModel.clearAllData()
                                confirmingClear = false
                            }
                        )
                    }
                    exportJson?.let {
                        OutlinedTextField(
                            value = it,
                            onValueChange = { },
                            label = { Text("Exported JSON") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            minLines = 6,
                            maxLines = 12,
                            readOnly = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
