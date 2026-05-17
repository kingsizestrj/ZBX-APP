package com.zbxapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.zbxapp.auth.BiometricGate
import com.zbxapp.ui.navigation.AppNavigation
import com.zbxapp.ui.theme.ZbxTheme

class MainActivity : FragmentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* user choice — nothing else to do */ }

    private var pendingEventId: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        pendingEventId = intent?.getStringExtra(EXTRA_EVENT_ID)
        val container = (application as ZbxApp).container
        setContent {
            ZbxTheme {
                BiometricGate {
                    val eventId = pendingEventId
                    AppNavigation(
                        container = container,
                        initialEventId = eventId,
                        onDeepLinkConsumed = { pendingEventId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_EVENT_ID)?.let { pendingEventId = it }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_EVENT_ID = "eventId"
    }
}
