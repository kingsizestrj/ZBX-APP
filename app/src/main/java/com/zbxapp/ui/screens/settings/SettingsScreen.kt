package com.zbxapp.ui.screens.settings

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zbxapp.di.AppContainer
import com.zbxapp.worker.ProblemsPollingWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val state by container.storage.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
                    Text("Servidor", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(server.baseUrl, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Autenticação: " + if (server.useBearerAuth) "Bearer (Zabbix 7.0+)" else "Auth in-body (≤6.4)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }

            Column {
                Text("Severidade mínima para notificações", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Apenas problemas com severidade igual ou maior geram push.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        0 to "Todas",
                        2 to "Warning",
                        3 to "Average",
                        4 to "High",
                        5 to "Disaster",
                    ).forEach { (sev, label) ->
                        FilterChip(
                            selected = state.minSeverity == sev,
                            onClick = { container.storage.saveMinSeverity(sev) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column {
                Text("Intervalo de verificação em background", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Mínimo do Android é 15 min. Mais frequente = mais bateria.",
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
                            label = { Text("${mins}m") },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column {
                Text("Problemas suprimidos", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Quando ativado, problemas em manutenção/suprimidos também aparecem na lista e geram notificações.",
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
                        label = { Text("Inclui suprimidos") },
                    )
                }
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
                Text("Sair (apaga credenciais salvas)")
            }

            Text(
                "ZBX • v0.1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
