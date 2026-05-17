package com.zbxapp.ui.screens.problems

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zbxapp.R
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

    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var hostFilter by rememberSaveable { mutableStateOf("") }
    var tagFilter by rememberSaveable { mutableStateOf("") }
    var severitiesRaw by rememberSaveable { mutableStateOf("") }
    val severitiesSelected = remember(severitiesRaw) {
        severitiesRaw.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    }

    val baseList = remember(state.problems, state.onlyUnacked) {
        state.problems.filter { p -> !state.onlyUnacked || p.acknowledged != "1" }
    }
    val visible = remember(baseList, searchQuery, hostFilter, tagFilter, severitiesSelected) {
        applyFilters(
            list = baseList,
            search = searchQuery,
            host = hostFilter,
            tag = tagFilter,
            severities = severitiesSelected,
        )
    }
    val isOffline = state.error != null && state.problems.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.problems_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.lastUpdatedMs > 0) {
                            Text(
                                text = stringResource(
                                    R.string.problems_summary,
                                    visible.size,
                                    formatRelative(state.lastUpdatedMs),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { filtersOpen = !filtersOpen }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(
                                if (filtersOpen) R.string.action_hide_filters else R.string.action_show_filters,
                            ),
                        )
                    }
                    IconButton(onClick = vm::refresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            QuickFilterRow(
                onlyUnacked = state.onlyUnacked,
                includeSuppressed = state.includeSuppressed,
                onUnackedToggle = vm::setOnlyUnacked,
                onIncludeSuppressedToggle = vm::setIncludeSuppressed,
            )

            AnimatedVisibility(
                visible = filtersOpen,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                AdvancedFiltersPanel(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    hostFilter = hostFilter,
                    onHostChange = { hostFilter = it },
                    tagFilter = tagFilter,
                    onTagChange = { tagFilter = it },
                    severitiesSelected = severitiesSelected,
                    onToggleSeverity = { sev ->
                        val updated = severitiesSelected.toMutableSet().apply {
                            if (!add(sev)) remove(sev)
                        }
                        severitiesRaw = updated.sorted().joinToString(",")
                    },
                    onClear = {
                        searchQuery = ""
                        hostFilter = ""
                        tagFilter = ""
                        severitiesRaw = ""
                    },
                )
            }

            if (isOffline) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.problems_offline_banner,
                            formatRelative(state.lastUpdatedMs),
                        ),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                state.error?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = err,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            Text(
                                text = stringResource(R.string.problems_empty),
                                style = MaterialTheme.typography.titleMedium,
                            )
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

private fun applyFilters(
    list: List<ZbxProblem>,
    search: String,
    host: String,
    tag: String,
    severities: Set<Int>,
): List<ZbxProblem> {
    val s = search.trim().lowercase()
    val h = host.trim().lowercase()
    val t = tag.trim().lowercase()
    return list.filter { p ->
        val sev = p.severity.toIntOrNull() ?: 0
        if (severities.isNotEmpty() && sev !in severities) return@filter false
        val hostNames = p.hosts.map { (it.name.ifBlank { it.host }).lowercase() }
        if (s.isNotEmpty()) {
            val nameMatch = p.name.lowercase().contains(s)
            val hostMatch = hostNames.any { it.contains(s) }
            if (!nameMatch && !hostMatch) return@filter false
        }
        if (h.isNotEmpty() && hostNames.none { it.contains(h) }) return@filter false
        if (t.isNotEmpty()) {
            val tagMatch = p.tags.any { tagObj ->
                tagObj.tag.lowercase().contains(t) ||
                    tagObj.value.lowercase().contains(t) ||
                    "${tagObj.tag}:${tagObj.value}".lowercase().contains(t)
            }
            if (!tagMatch) return@filter false
        }
        true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickFilterRow(
    onlyUnacked: Boolean,
    includeSuppressed: Boolean,
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
        FilterChip(
            selected = onlyUnacked,
            onClick = { onUnackedToggle(!onlyUnacked) },
            label = { Text(stringResource(R.string.filter_only_unacked)) },
        )
        FilterChip(
            selected = includeSuppressed,
            onClick = { onIncludeSuppressedToggle(!includeSuppressed) },
            label = { Text(stringResource(R.string.filter_include_suppressed)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFiltersPanel(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    hostFilter: String,
    onHostChange: (String) -> Unit,
    tagFilter: String,
    onTagChange: (String) -> Unit,
    severitiesSelected: Set<Int>,
    onToggleSeverity: (Int) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                label = { Text(stringResource(R.string.filter_search_placeholder)) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.filter_clear))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hostFilter,
                    onValueChange = onHostChange,
                    label = { Text(stringResource(R.string.filter_host_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = tagFilter,
                    onValueChange = onTagChange,
                    label = { Text(stringResource(R.string.filter_tag_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.filter_severity_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val severities = listOf(
                    1 to R.string.severity_information,
                    2 to R.string.severity_warning,
                    3 to R.string.severity_average,
                    4 to R.string.severity_high,
                    5 to R.string.severity_disaster,
                )
                severities.forEach { (sev, labelRes) ->
                    FilterChip(
                        selected = sev in severitiesSelected,
                        onClick = { onToggleSeverity(sev) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.filter_clear))
                }
            }
        }
    }
}

@Composable
private fun ProblemRow(problem: ZbxProblem, onClick: () -> Unit) {
    val severity = problem.severity.toIntOrNull() ?: 0
    val color = Severity.colorFor(severity)
    val host = problem.hosts.firstOrNull()?.name?.ifBlank { problem.hosts.firstOrNull()?.host }.orEmpty()
    val acked = problem.acknowledged == "1"
    val rowDescription = stringResource(R.string.problems_title) + ": " + problem.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics { contentDescription = rowDescription },
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
                            text = stringResource(R.string.filter_chip_ack),
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
                text = problem.name.ifBlank { stringResource(R.string.problems_no_description) },
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
    val label = when (severity) {
        1 -> stringResource(R.string.severity_information)
        2 -> stringResource(R.string.severity_warning)
        3 -> stringResource(R.string.severity_average)
        4 -> stringResource(R.string.severity_high)
        5 -> stringResource(R.string.severity_disaster)
        else -> stringResource(R.string.severity_not_classified)
    }
    Surface(
        color = Severity.colorFor(severity),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = label.uppercase(),
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

@Composable
private fun formatRelative(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    val diff = ((System.currentTimeMillis() - timeMs) / 1000).coerceAtLeast(0)
    return when {
        diff < 60 -> stringResource(R.string.state_now)
        diff < 3600 -> stringResource(R.string.state_minutes_ago, (diff / 60).toInt())
        else -> stringResource(
            R.string.state_at_time,
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs)),
        )
    }
}
