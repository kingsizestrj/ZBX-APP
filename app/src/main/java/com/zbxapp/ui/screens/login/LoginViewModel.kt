package com.zbxapp.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zbxapp.data.repository.ZabbixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class LoginError { MissingFields, InvalidUrl, ConnectFailed }

data class LoginUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorType: LoginError? = null,
    val errorDetail: String? = null,
) {
    val error: String?
        get() = when (errorType) {
            null -> null
            LoginError.ConnectFailed -> errorDetail ?: "connect_failed"
            LoginError.MissingFields -> "missing_fields"
            LoginError.InvalidUrl -> "invalid_url"
        }
}

class LoginViewModel(private val repository: ZabbixRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onUrlChange(value: String) { _state.value = _state.value.copy(baseUrl = value, errorType = null, errorDetail = null) }
    fun onUserChange(value: String) { _state.value = _state.value.copy(username = value, errorType = null, errorDetail = null) }
    fun onPasswordChange(value: String) { _state.value = _state.value.copy(password = value, errorType = null, errorDetail = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.baseUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(errorType = LoginError.MissingFields)
            return
        }
        val normalized = normalizeUrl(s.baseUrl)
        if (normalized == null) {
            _state.value = s.copy(errorType = LoginError.InvalidUrl)
            return
        }
        _state.value = s.copy(isLoading = true, errorType = null, errorDetail = null, baseUrl = normalized)
        viewModelScope.launch {
            val result = repository.probeAndLogin(normalized, s.username.trim(), s.password)
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false)
                onSuccess()
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorType = LoginError.ConnectFailed,
                    errorDetail = e.message,
                )
            }
        }
    }

    companion object {
        internal fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.contains("://") -> return null
                else -> "https://$trimmed"
            }
            val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
            if (host.isBlank() || host.contains(' ')) return null
            if (!host.contains('.') && host != "localhost" && !host.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+(:\\d+)?$"))) {
                if (!host.contains(':')) return null
            }
            return withScheme
        }
    }
}
