package com.zbxapp.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zbxapp.ZbxApp
import com.zbxapp.notification.NotificationHelper
import java.util.concurrent.TimeUnit

class ProblemsPollingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ZbxApp
        val repo = app.container.repository
        val storage = app.container.storage

        val state = storage.state.value
        if (!state.isConfigured) return Result.success()

        val problems = repo.fetchProblems().getOrElse { return Result.retry() }

        val lastSeen = storage.getLastSeenEventId()?.toLongOrNull() ?: 0L
        val newProblems = problems.filter { (it.eventid.toLongOrNull() ?: 0L) > lastSeen }

        if (newProblems.isNotEmpty()) {
            val filtered = newProblems.filter {
                (it.severity.toIntOrNull() ?: 0) >= state.minSeverity
            }
            NotificationHelper.notifyNewProblems(applicationContext, filtered)
        }

        val maxId = problems.maxOfOrNull { it.eventid.toLongOrNull() ?: 0L } ?: lastSeen
        if (maxId > lastSeen) storage.saveLastSeenEventId(maxId.toString())

        return Result.success()
    }

    companion object {
        private const val UNIQUE = "problems_polling"

        fun schedule(context: Context, intervalMinutes: Int) {
            val safeInterval = intervalMinutes.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<ProblemsPollingWorker>(
                safeInterval, TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
