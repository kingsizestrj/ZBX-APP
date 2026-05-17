package com.zbxapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import com.zbxapp.di.AppContainer
import com.zbxapp.notification.NotificationHelper
import com.zbxapp.worker.ProblemsPollingWorker

class ZbxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        ProblemsPollingWorker.schedule(this, container.storage.state.value.pollIntervalMinutes)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            NotificationHelper.CHANNEL_PROBLEMS,
            getString(R.string.notification_channel_problems_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_problems_desc)
            enableVibration(true)
        }
        mgr.createNotificationChannel(channel)
    }
}
