package com.zbxapp.ui.screens.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zbxapp.data.api.models.ZbxHistoryPoint
import com.zbxapp.di.AppContainer
import com.zbxapp.ui.theme.ZbxOnSurfaceMuted
import com.zbxapp.ui.theme.ZbxPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(container: AppContainer, itemId: String, onBack: () -> Unit) {
    val vm: GraphViewModel = viewModel(
        key = "graph-$itemId",
        factory = viewModelFactory {
            initializer { GraphViewModel(container.repository, itemId) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item?.name ?: "Gráfico") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.item?.let { item ->
                Text(
                    text = "${item.name}${if (item.units.isNotBlank()) " (${item.units})" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (item.lastvalue.isNotBlank()) {
                    Text(
                        "atual: ${item.lastvalue}${if (item.units.isNotBlank()) " ${item.units}" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { vm.setRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                when {
                    state.isLoading -> Text("Carregando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error != null && state.history.isEmpty() -> Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.history.isEmpty() -> Text(
                        "Sem pontos no período",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LineChart(
                        points = state.history,
                        units = state.item?.units.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                val values = state.history.mapNotNull { it.value.toDoubleOrNull() }
                if (values.isNotEmpty()) {
                    Text(
                        "min ${values.min().fmt()} · max ${values.max().fmt()} · avg ${values.average().fmt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LineChart(points: List<ZbxHistoryPoint>, units: String, modifier: Modifier = Modifier) {
    val xy = points.mapNotNull { p ->
        val x = p.clock.toDoubleOrNull() ?: return@mapNotNull null
        val y = p.value.toDoubleOrNull() ?: return@mapNotNull null
        x to y
    }
    if (xy.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Pontos insuficientes", color = ZbxOnSurfaceMuted)
        }
        return
    }

    val minX = xy.first().first
    val maxX = xy.last().first
    val minY = xy.minOf { it.second }
    val maxY = xy.maxOf { it.second }
    val rangeX = (maxX - minX).coerceAtLeast(1.0)
    val rangeY = (maxY - minY).let { if (it == 0.0) 1.0 else it }

    val gridColor = ZbxOnSurfaceMuted.copy(alpha = 0.25f)
    val axisLabelColor = ZbxOnSurfaceMuted

    Canvas(modifier = modifier) {
        val leftPad = 56f
        val bottomPad = 28f
        val topPad = 8f
        val rightPad = 12f
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad

        // Horizontal gridlines + y labels
        val ySteps = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (i in 0..ySteps) {
            val frac = i / ySteps.toFloat()
            val y = topPad + h * (1f - frac)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + w, y),
                strokeWidth = 1f,
                pathEffect = dashEffect,
            )
            val value = minY + rangeY * frac
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (axisLabelColor.alpha * 255).toInt(),
                        (axisLabelColor.red * 255).toInt(),
                        (axisLabelColor.green * 255).toInt(),
                        (axisLabelColor.blue * 255).toInt(),
                    )
                    textSize = 28f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                drawText(value.fmt(), leftPad - 6f, y + 8f, paint)
            }
        }

        // X labels (start, middle, end)
        val xLabelTimes = listOf(minX, (minX + maxX) / 2.0, maxX)
        xLabelTimes.forEachIndexed { idx, t ->
            val frac = if (rangeX == 0.0) 0f else ((t - minX) / rangeX).toFloat()
            val xPos = leftPad + w * frac
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (axisLabelColor.alpha * 255).toInt(),
                        (axisLabelColor.red * 255).toInt(),
                        (axisLabelColor.green * 255).toInt(),
                        (axisLabelColor.blue * 255).toInt(),
                    )
                    textSize = 28f
                    isAntiAlias = true
                    textAlign = when (idx) {
                        0 -> android.graphics.Paint.Align.LEFT
                        xLabelTimes.lastIndex -> android.graphics.Paint.Align.RIGHT
                        else -> android.graphics.Paint.Align.CENTER
                    }
                }
                drawText(formatTime(t.toLong()), xPos, size.height - 6f, paint)
            }
        }

        // Draw the line + filled area
        val path = Path()
        val fillPath = Path()
        xy.forEachIndexed { i, (x, y) ->
            val px = leftPad + (w * ((x - minX) / rangeX)).toFloat()
            val py = topPad + (h * (1.0 - (y - minY) / rangeY)).toFloat()
            if (i == 0) {
                path.moveTo(px, py)
                fillPath.moveTo(px, topPad + h)
                fillPath.lineTo(px, py)
            } else {
                path.lineTo(px, py)
                fillPath.lineTo(px, py)
            }
            if (i == xy.lastIndex) {
                fillPath.lineTo(px, topPad + h)
                fillPath.close()
            }
        }
        drawPath(
            path = fillPath,
            color = ZbxPrimary.copy(alpha = 0.18f),
        )
        drawPath(
            path = path,
            color = ZbxPrimary,
            style = Stroke(width = 4f),
        )
    }
}

private fun Double.fmt(): String = when {
    this >= 1_000_000_000 -> "%.2fG".format(this / 1_000_000_000)
    this >= 1_000_000 -> "%.2fM".format(this / 1_000_000)
    this >= 1_000 -> "%.2fK".format(this / 1_000)
    this >= 10 -> "%.1f".format(this)
    else -> "%.2f".format(this)
}

private fun formatTime(epochSec: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSec * 1000))
