package com.zbxapp.ui.screens.problems

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.di.AppContainer
import com.zbxapp.ui.theme.Severity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    container: AppContainer,
    onOpenProblem: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: ProblemsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProblemsViewModel(container.repository, container.storage) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val visible = remember(state) { vm.visibleProblems() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Problems", style = MaterialTheme.typography.titleLarge)
                        if (state.lastUpdatedMs > 0) {
                            Text(
                                text = "${visible.size} ativos · atualizado ${formatRelative(state.lastUpdatedMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            FilterRow(
                minSeverity = state.minSeverityFilter,
                onlyUnacked = state.onlyUnacked,
                includeSuppressed = state.includeSuppressed,
                onSeverityChange = vm::setSeverityFilter,
                onUnackedToggle = vm::setOnlyUnacked,
                onIncludeSuppressedToggle = vm::setIncludeSuppressed,
            )

            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Carregando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF59E47A),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Nenhum problema ativo", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        items(visible, key = { it.eventid }) { problem ->
                            ProblemRow(problem = problem, onClick = { onOpenProblem(problem.eventid) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    minSeverity: Int,
    onlyUnacked: Boolean,
    includeSuppressed: Boolean,
    onSeverityChange: (Int) -> Unit,
    onUnackedToggle: (Boolean) -> Unit,
    onIncludeSuppressedToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            0 to "Todas",
            2 to "≥ Warning",
            3 to "≥ Average",
            4 to "≥ High",
        ).forEach { (sev, label) ->
            FilterChip(
                selected = minSeverity == sev,
                onClick = { onSeverityChange(sev) },
                label = { Text(label) },
            )
        }
        FilterChip(
            selected = onlyUnacked,
            onClick = { onUnackedToggle(!onlyUnacked) },
            label = { Text("Não ack") },
        )
        FilterChip(
            selected = includeSuppressed,
            onClick = { onIncludeSuppressedToggle(!includeSuppressed) },
            label = { Text("Inclui suprimidos") },
        )
    }
}

@Composable
private fun ProblemRow(problem: ZbxProblem, onClick: () -> Unit) {
    val severity = problem.severity.toIntOrNull() ?: 0
    val color = Severity.colorFor(severity)
    val host = problem.hosts.firstOrNull()?.name?.ifBlank { problem.hosts.firstOrNull()?.host }.orEmpty()
    val acked = problem.acknowledged == "1"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityChip(severity)
                if (acked) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "ACK",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatProblemAge(problem.clock.toLongOrNull() ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = problem.name.ifBlank { "Sem descrição" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            if (host.isNotEmpty()) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (problem.opdata.isNotBlank()) {
                Text(
                    text = problem.opdata,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SeverityChip(severity: Int) {
    Surface(
        color = Severity.colorFor(severity),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = Severity.labelFor(severity).uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.Black,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatProblemAge(clockSeconds: Long): String {
    if (clockSeconds <= 0L) return ""
    val nowSec = System.currentTimeMillis() / 1000
    val ageSec = (nowSec - clockSeconds).coerceAtLeast(0)
    return when {
        ageSec < 60 -> "${ageSec}s"
        ageSec < 3600 -> "${ageSec / 60}m"
        ageSec < 86_400 -> "${ageSec / 3600}h"
        else -> "${ageSec / 86_400}d"
    }
}

private fun formatRelative(timeMs: Long): String {
    val diff = ((System.currentTimeMillis() - timeMs) / 1000).coerceAtLeast(0)
    return when {
        diff < 60 -> "agora"
        diff < 3600 -> "há ${diff / 60} min"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
    }
}
