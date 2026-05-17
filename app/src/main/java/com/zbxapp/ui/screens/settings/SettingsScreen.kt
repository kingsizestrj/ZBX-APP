package com.zbxapp.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zbxapp.R
import com.zbxapp.auth.isBiometricAvailable
import com.zbxapp.di.AppContainer
import com.zbxapp.ui.theme.ThemeMode
import com.zbxapp.ui.theme.UiPreferences
import com.zbxapp.worker.ProblemsPollingWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val state by container.storage.state.collectAsStateWithLifecycle()
    val uiPrefs by UiPreferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val biometricAvailable = remember { isBiometricAvailable(context) }
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            state.server?.let { server ->
                Column {
                    Text(
                        text = stringResource(R.string.settings_section_server),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(server.baseUrl, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(
                            if (server.useBearerAuth) R.string.settings_auth_bearer else R.string.settings_auth_inbody,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }

            Column {
                Text(
                    text = stringResource(R.string.settings_min_severity_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_min_severity_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val severityOptions = listOf(
                        0 to R.string.filter_all_severities,
                        2 to R.string.severity_warning,
                        3 to R.string.severity_average,
                        4 to R.string.severity_high,
                        5 to R.string.severity_disaster,
                    )
                    severityOptions.forEach { (sev, labelRes) ->
                        FilterChip(
                            selected = state.minSeverity == sev,
                            onClick = { container.storage.saveMinSeverity(sev) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column {
                Text(
                    text = stringResource(R.string.settings_poll_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_poll_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(15, 30, 60).forEach { mins ->
                        FilterChip(
                            selected = state.pollIntervalMinutes == mins,
                            onClick = {
                                container.storage.savePollIntervalMinutes(mins)
                                ProblemsPollingWorker.schedule(context, mins)
                            },
                            label = { Text(stringResource(R.string.settings_poll_minutes, mins)) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column {
                Text(
                    text = stringResource(R.string.settings_suppressed_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_suppressed_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = state.includeSuppressed,
                        onClick = { container.storage.saveIncludeSuppressed(!state.includeSuppressed) },
                        label = { Text(stringResource(R.string.filter_include_suppressed)) },
                    )
                }
            }

            HorizontalDivider()

            Column {
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_theme_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val themeOptions = listOf(
                        ThemeMode.SYSTEM to R.string.settings_theme_system,
                        ThemeMode.LIGHT to R.string.settings_theme_light,
                        ThemeMode.DARK to R.string.settings_theme_dark,
                    )
                    themeOptions.forEach { (mode, labelRes) ->
                        FilterChip(
                            selected = uiPrefs.themeMode == mode,
                            onClick = { UiPreferences.setThemeMode(mode) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }

            if (dynamicColorAvailable) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiPrefs.useDynamicColor,
                        onCheckedChange = { UiPreferences.setUseDynamicColor(it) },
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_biometric_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(
                            if (biometricAvailable) R.string.settings_biometric_desc
                            else R.string.settings_biometric_unavailable,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiPrefs.requireBiometric && biometricAvailable,
                    onCheckedChange = { UiPreferences.setRequireBiometric(it) },
                    enabled = biometricAvailable,
                )
            }

            HorizontalDivider()

            Button(
                onClick = {
                    scope.launch {
                        container.repository.logout()
                        ProblemsPollingWorker.cancel(context)
                        onLoggedOut()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_logout))
            }

            Text(
                text = stringResource(R.string.settings_version),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
