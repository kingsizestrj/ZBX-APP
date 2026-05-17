package com.zbxapp.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zbxapp.MainActivity
import com.zbxapp.ZbxApp
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.worker.ProblemsPollingWorker
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget that surfaces the most severe cached problems. Reads from
 * the Room cache (no network call) so it's cheap to update; tapping the widget
 * also enqueues a one-time WorkManager request that refreshes the cache.
 */
class ProblemsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? ZbxApp
        val problems: List<ZbxProblem> = app?.container?.repository
            ?.observeCachedProblems()
            ?.first()
            .orEmpty()
            .sortedWith(
                compareByDescending<ZbxProblem> { it.severity.toIntOrNull() ?: 0 }
                    .thenByDescending { it.clock.toLongOrNull() ?: 0L },
            )

        provideContent {
            GlanceTheme {
                WidgetContent(context = context, problems = problems)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, problems: List<ZbxProblem>) {
        val refreshAction: Action = actionRunCallback<RefreshWidgetAction>()
        val openAppAction: Action = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("from_widget", true)
            },
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
                .background(Color(0xFF0F1419))
                .clickable(openAppAction),
        ) {
            Header(total = problems.size, refreshAction = refreshAction)
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (problems.isEmpty()) {
                EmptyState()
            } else {
                problems.take(3).forEach { p ->
                    ProblemRow(p)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun Header(total: Int, refreshAction: Action) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Zabbix • $total ${if (total == 1) "problema" else "problemas"}",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFE6EAEE)),
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "↻",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF7499FF)),
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(refreshAction),
            )
        }
    }

    @Composable
    private fun ProblemRow(p: ZbxProblem) {
        val severity = p.severity.toIntOrNull() ?: 0
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Severity chip — a coloured square. Glance Compose can't draw arbitrary
            // shapes; a small filled Box is the lightweight equivalent.
            Box(
                modifier = GlanceModifier
                    .size(10.dp)
                    .background(severityColor(severity)),
            ) { }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = p.name.ifBlank { "(sem descrição)" },
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFE6EAEE)),
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val host = p.hosts.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                    ?: p.hosts.firstOrNull()?.host.orEmpty()
                if (host.isNotBlank()) {
                    Text(
                        text = host,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF8A95A0)),
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Sem problemas ativos",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF8A95A0)),
                ),
            )
        }
    }

    private fun severityColor(severity: Int): Color = when (severity) {
        1 -> Color(0xFF7499FF) // Information
        2 -> Color(0xFFFFC859) // Warning
        3 -> Color(0xFFFFA059) // Average
        4 -> Color(0xFFE97659) // High
        5 -> Color(0xFFE45959) // Disaster
        else -> Color(0xFF97AAB3) // Not classified
    }
}

/**
 * Tap-on-refresh callback: enqueues a one-time copy of [ProblemsPollingWorker]
 * so the cache is freshened without waiting for the periodic schedule.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        val req = OneTimeWorkRequestBuilder<ProblemsPollingWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(req)
        // Force the widget to re-read the cache after the worker finishes. Glance
        // does this implicitly on next update; we just trigger one immediately.
        ProblemsWidget().update(context, glanceId)
    }
}

