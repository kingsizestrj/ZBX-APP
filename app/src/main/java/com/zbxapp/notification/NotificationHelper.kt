package com.zbxapp.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zbxapp.MainActivity
import com.zbxapp.R
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.ui.theme.Severity

object NotificationHelper {
    const val CHANNEL_PROBLEMS = "channel_problems"

    fun notifyNewProblems(context: Context, problems: List<ZbxProblem>) {
        if (problems.isEmpty()) return
        if (!canPostNotifications(context)) return
        val mgr = NotificationManagerCompat.from(context)

        problems.sortedByDescending { it.severity.toIntOrNull() ?: 0 }
            .take(10)
            .forEach { p ->
                val severity = p.severity.toIntOrNull() ?: 0
                val host = p.hosts.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                    ?: p.hosts.firstOrNull()?.host?.takeIf { it.isNotBlank() }
                val label = Severity.labelFor(severity)
                val title = if (host != null) "[$label] $host" else context.getString(R.string.notif_new_problem)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    p.eventid.hashCode(),
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("eventId", p.eventid)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_PROBLEMS)
                    .setSmallIcon(R.drawable.ic_splash)
                    .setColor(Severity.colorFor(severity).toArgb())
                    .setColorized(true)
                    .setContentTitle(title)
                    .setContentText(p.name.ifBlank { context.getString(R.string.notif_no_description) })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(p.name))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
                mgr.notify(p.eventid.hashCode(), notification)
            }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
