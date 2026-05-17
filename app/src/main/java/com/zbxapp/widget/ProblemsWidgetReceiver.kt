package com.zbxapp.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidgetProvider that delegates to the Glance implementation in [ProblemsWidget].
 * Must be declared in AndroidManifest.xml with a `<receiver>` entry pointing at
 * `@xml/zbx_widget_info` (the AppWidgetProviderInfo metadata).
 */
class ProblemsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProblemsWidget()
}
