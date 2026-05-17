package com.zbxapp.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zbxapp.R
import com.zbxapp.ui.theme.UiPreferences

private fun canAuthenticate(context: Context): Int {
    val mgr = BiometricManager.from(context)
    return mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
}

fun isBiometricAvailable(context: Context): Boolean {
    return canAuthenticate(context) == BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
fun BiometricGate(content: @Composable () -> Unit) {
    val prefs by UiPreferences.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (!prefs.requireBiometric) {
        content()
        return
    }

    val activity = context as? FragmentActivity
    if (activity == null) {
        content()
        return
    }

    val biometricCheck = remember(prefs.requireBiometric) { canAuthenticate(context) }
    if (biometricCheck != BiometricManager.BIOMETRIC_SUCCESS) {
        content()
        return
    }

    var authenticated by remember { mutableStateOf(false) }
    var promptShowing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun launchPrompt() {
        if (promptShowing) return
        promptShowing = true
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptShowing = false
                error = null
                authenticated = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptShowing = false
                error = errString.toString()
            }

            override fun onAuthenticationFailed() {
                promptShowing = false
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_prompt_title))
            .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(context.getString(R.string.biometric_prompt_negative))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(prefs.requireBiometric) {
        if (!authenticated) launchPrompt()
    }

    if (authenticated) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.biometric_locked),
                    style = MaterialTheme.typography.titleMedium,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launchPrompt() }) {
                    Text(stringResource(R.string.biometric_retry))
                }
            }
        }
    }
}
