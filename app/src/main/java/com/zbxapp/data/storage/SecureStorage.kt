package com.zbxapp.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ServerConfig(
    val baseUrl: String,
    val useBearerAuth: Boolean,
) {
    val apiUrl: String = run {
        val trimmed = baseUrl.trimEnd('/')
        if (trimmed.endsWith("/api_jsonrpc.php", ignoreCase = true)) trimmed
        else "$trimmed/api_jsonrpc.php"
    }
}

/**
 * Holds Zabbix server config, credentials and auth token in EncryptedSharedPreferences.
 * Token is required for everything but login; credentials are stored so we can refresh
 * the token in background (WorkManager) without prompting the user.
 */
class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AuthState> = _state

    fun saveServer(baseUrl: String, useBearerAuth: Boolean) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trimEnd('/'))
            .putBoolean(KEY_USE_BEARER, useBearerAuth)
            .apply()
        refresh()
    }

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
        refresh()
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        refresh()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        refresh()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        refresh()
    }

    fun saveLastSeenEventId(eventId: String) {
        prefs.edit().putString(KEY_LAST_EVENT, eventId).apply()
    }

    fun getLastSeenEventId(): String? = prefs.getString(KEY_LAST_EVENT, null)

    fun saveMinSeverity(severity: Int) {
        prefs.edit().putInt(KEY_MIN_SEVERITY, severity).apply()
        refresh()
    }

    fun savePollIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_POLL_MIN, minutes).apply()
        refresh()
    }

    fun saveIncludeSuppressed(value: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_SUPPRESSED, value).apply()
        refresh()
    }

    private fun refresh() {
        _state.value = loadState()
    }

    private fun loadState(): AuthState {
        val baseUrl = prefs.getString(KEY_BASE_URL, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        val token = prefs.getString(KEY_TOKEN, null)
        val useBearer = prefs.getBoolean(KEY_USE_BEARER, true)
        val minSev = prefs.getInt(KEY_MIN_SEVERITY, 0)
        val pollMin = prefs.getInt(KEY_POLL_MIN, 15)
        val includeSuppressed = prefs.getBoolean(KEY_INCLUDE_SUPPRESSED, false)
        val server = baseUrl?.let { ServerConfig(it, useBearer) }
        return AuthState(
            server = server,
            username = username,
            password = password,
            token = token,
            minSeverity = minSev,
            pollIntervalMinutes = pollMin,
            includeSuppressed = includeSuppressed,
        )
    }

    companion object {
        private const val FILE_NAME = "zbx_secure_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USE_BEARER = "use_bearer"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOKEN = "token"
        private const val KEY_LAST_EVENT = "last_event_id"
        private const val KEY_MIN_SEVERITY = "min_severity"
        private const val KEY_POLL_MIN = "poll_interval_min"
        private const val KEY_INCLUDE_SUPPRESSED = "include_suppressed"
    }
}

data class AuthState(
    val server: ServerConfig?,
    val username: String?,
    val password: String?,
    val token: String?,
    val minSeverity: Int,
    val pollIntervalMinutes: Int,
    val includeSuppressed: Boolean = false,
) {
    val isConfigured: Boolean get() = server != null && !username.isNullOrBlank() && !password.isNullOrBlank()
    val isAuthenticated: Boolean get() = isConfigured && !token.isNullOrBlank()
}
