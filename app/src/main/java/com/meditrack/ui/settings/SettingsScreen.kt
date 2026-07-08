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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meditrack.R
import com.meditrack.data.repository.ThemeMode
import com.meditrack.ui.components.BasicCard
import com.meditrack.ui.components.ConfirmingTextButton
import com.meditrack.ui.components.ScreenHeader
import com.meditrack.ui.components.WarningBand
import com.meditrack.ui.labelRes
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
            viewModel.setMessage(context.getString(R.string.msg_export_saved))
        }.onFailure { error ->
            viewModel.setMessage(
                context.getString(
                    R.string.msg_export_failed,
                    error.message ?: context.getString(R.string.msg_export_unknown_error)
                )
            )
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
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle)
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
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                    LanguageSelector()
                }
            }
        }
        item {
            BasicCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.settings_defaults), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_defaults_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ThemeModeSelector(
                        selected = settings.themeMode,
                        onSelected = viewModel::setThemeMode
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it },
                            label = { Text(stringResource(R.string.settings_low_stock_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = { viewModel.saveLowStockThreshold(threshold) }) {
                            Text(stringResource(R.string.action_save))
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
                    Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)
                    SettingSwitchRow(
                        label = stringResource(R.string.settings_reminders_enabled),
                        checked = settings.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled
                    )
                    SettingSwitchRow(
                        label = stringResource(R.string.settings_vibration_enabled),
                        checked = settings.vibrationEnabled,
                        onCheckedChange = viewModel::setVibrationEnabled
                    )
                    SettingSwitchRow(
                        label = stringResource(R.string.settings_alarm_sound),
                        checked = settings.alarmSoundEnabled,
                        onCheckedChange = viewModel::setAlarmSoundEnabled
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
                    Text(stringResource(R.string.settings_local_data), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                pendingFileExport = true
                                viewModel.exportJson()
                            }
                        ) {
                            Text(stringResource(R.string.settings_export))
                        }
                        ConfirmingTextButton(
                            label = stringResource(R.string.settings_clear),
                            confirmingLabel = stringResource(R.string.settings_clear_confirm),
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
                            label = { Text(stringResource(R.string.settings_exported_json)) },
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
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.values().forEach { mode ->
                val selectedMode = mode == selected
                if (selectedMode) {
                    Button(
                        onClick = { onSelected(mode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(mode.labelRes()))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(mode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(mode.labelRes()))
                    }
                }
            }
        }
    }
}

/**
 * English / Bengali toggle. Uses AndroidX per-app locales, which persist the choice and recreate
 * the activity so the whole app immediately switches language.
 */
@Composable
private fun LanguageSelector() {
    val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val isBengali = currentTags.startsWith("bn")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LanguageButton(
            label = stringResource(R.string.lang_english),
            selected = !isBengali,
            modifier = Modifier.weight(1f)
        ) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        LanguageButton(
            label = stringResource(R.string.lang_bengali),
            selected = isBengali,
            modifier = Modifier.weight(1f)
        ) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("bn"))
        }
    }
}

@Composable
private fun LanguageButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
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
