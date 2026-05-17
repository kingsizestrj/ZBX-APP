package com.zbxapp.ui.screens.problemdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zbxapp.R
import com.zbxapp.data.api.models.ZbxAcknowledge
import com.zbxapp.data.api.models.ZbxItem
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.di.AppContainer
import com.zbxapp.ui.theme.Severity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemDetailScreen(
    container: AppContainer,
    eventId: String,
    onBack: () -> Unit,
    onOpenItemGraph: (String) -> Unit,
) {
    val vm: ProblemDetailViewModel = viewModel(
        key = "detail-$eventId",
        factory = viewModelFactory {
            initializer { ProblemDetailViewModel(container.repository, eventId) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var ackDialog by remember { mutableStateOf<AckDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
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
        val problem = state.problem
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        if (problem == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(state.error ?: stringResource(R.string.detail_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(problem)
            ActionsRow(
                onAck = { ackDialog = AckDialog.Ack },
                onClose = { ackDialog = AckDialog.Close },
                onComment = { ackDialog = AckDialog.Comment },
                inProgress = state.actionInProgress,
            )
            state.actionMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            if (problem.tags.isNotEmpty()) {
                TagsSection(problem)
            }
            if (state.items.isNotEmpty()) {
                ItemsSection(items = state.items, onItemClick = onOpenItemGraph)
            }
            if (problem.acknowledges.isNotEmpty()) {
                HistorySection(problem.acknowledges)
            }
        }
    }

    ackDialog?.let { dialog ->
        AckMessageDialog(
            type = dialog,
            onDismiss = { ackDialog = null },
            onConfirm = { msg ->
                when (dialog) {
                    AckDialog.Ack -> vm.acknowledge(msg)
                    AckDialog.Close -> vm.closeProblem(msg)
                    AckDialog.Comment -> vm.addMessage(msg)
                }
                ackDialog = null
            },
        )
    }
}

private enum class AckDialog { Ack, Close, Comment }

@Composable
private fun Header(problem: ZbxProblem) {
    val severity = problem.severity.toIntOrNull() ?: 0
    val host = problem.hosts.firstOrNull()?.name?.ifBlank { problem.hosts.firstOrNull()?.host }.orEmpty()
    val clock = problem.clock.toLongOrNull() ?: 0L
    val sevLabel = when (severity) {
        1 -> stringResource(R.string.severity_information)
        2 -> stringResource(R.string.severity_warning)
        3 -> stringResource(R.string.severity_average)
        4 -> stringResource(R.string.severity_high)
        5 -> stringResource(R.string.severity_disaster)
        else -> stringResource(R.string.severity_not_classified)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Severity.colorFor(severity)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = sevLabel.uppercase(),
                color = Severity.colorFor(severity),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(8.dp))
            if (problem.acknowledged == "1") {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = stringResource(R.string.filter_chip_ack),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            text = problem.name.ifBlank { stringResource(R.string.problems_no_description) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (host.isNotBlank()) {
            Text(
                text = stringResource(R.string.detail_host, host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (problem.opdata.isNotBlank()) {
            Text(problem.opdata, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.detail_started, formatDateTime(clock)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionsRow(onAck: () -> Unit, onClose: () -> Unit, onComment: () -> Unit, inProgress: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onAck,
            enabled = !inProgress,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            Icon(Icons.Default.Done, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.detail_action_ack_short))
        }
        Button(
            onClick = onClose,
            enabled = !inProgress,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.detail_action_close))
        }
        OutlinedButton(
            onClick = onComment,
            enabled = !inProgress,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            Text(stringResource(R.string.detail_action_comment))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(problem: ZbxProblem) {
    Column {
        SectionTitle(stringResource(R.string.detail_section_tags))
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            problem.tags.forEach { tag ->
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            if (tag.value.isNotBlank()) "${tag.tag}: ${tag.value}" else tag.tag,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}

@Composable
private fun ItemsSection(items: List<ZbxItem>, onItemClick: (String) -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.detail_section_items))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item.itemid) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            Text(
                                text = stringResource(R.string.detail_item_key, item.key_),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (item.lastvalue.isNotBlank()) {
                                val combined = if (item.units.isNotBlank()) "${item.lastvalue} ${item.units}" else item.lastvalue
                                Text(
                                    text = stringResource(R.string.detail_item_current, combined),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (index < items.lastIndex) HorizontalDivider(color = Color.Black.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun HistorySection(acks: List<ZbxAcknowledge>) {
    Column {
        SectionTitle(stringResource(R.string.detail_section_history))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                val sorted = acks.sortedByDescending { it.clock.toLongOrNull() ?: 0L }
                sorted.forEachIndexed { idx, ack ->
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = formatDateTime(ack.clock.toLongOrNull() ?: 0L),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (ack.message.isNotBlank()) {
                            Text(ack.message, style = MaterialTheme.typography.bodySmall)
                        }
                        val flags = describeAckAction(ack.action.toIntOrNull() ?: 0)
                        if (flags.isNotEmpty()) {
                            Text(
                                text = flags,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (idx < sorted.lastIndex) HorizontalDivider(color = Color.Black.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun AckMessageDialog(type: AckDialog, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var msg by remember { mutableStateOf("") }
    val title = when (type) {
        AckDialog.Ack -> stringResource(R.string.detail_dialog_ack_title)
        AckDialog.Close -> stringResource(R.string.detail_dialog_close_title)
        AckDialog.Comment -> stringResource(R.string.detail_dialog_comment_title)
    }
    val confirmLabel = when (type) {
        AckDialog.Ack -> stringResource(R.string.detail_confirm_ack)
        AckDialog.Close -> stringResource(R.string.detail_confirm_close)
        AckDialog.Comment -> stringResource(R.string.detail_confirm_comment)
    }
    val messageLabel = if (type == AckDialog.Comment) {
        stringResource(R.string.detail_dialog_message_required)
    } else {
        stringResource(R.string.detail_dialog_message_optional)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = msg,
                onValueChange = { msg = it },
                label = { Text(messageLabel) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(msg) },
                enabled = type != AckDialog.Comment || msg.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun describeAckAction(action: Int): String {
    if (action == 0) return ""
    val parts = mutableListOf<String>()
    if (action and 1 != 0) parts += stringResource(R.string.detail_history_closed)
    if (action and 2 != 0) parts += stringResource(R.string.detail_history_ack)
    if (action and 4 != 0) parts += stringResource(R.string.detail_history_message)
    if (action and 8 != 0) parts += stringResource(R.string.detail_history_severity)
    if (action and 16 != 0) parts += stringResource(R.string.detail_history_unacked)
    if (action and 32 != 0) parts += stringResource(R.string.detail_history_suppressed)
    return parts.joinToString(", ")
}

private fun formatDateTime(clockSeconds: Long): String {
    if (clockSeconds <= 0L) return "-"
    return SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(clockSeconds * 1000))
}
