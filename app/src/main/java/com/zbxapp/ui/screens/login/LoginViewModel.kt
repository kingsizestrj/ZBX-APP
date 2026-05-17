package com.zbxapp.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zbxapp.data.repository.ZabbixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val repository: ZabbixRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onUrlChange(value: String) { _state.value = _state.value.copy(baseUrl = value, error = null) }
    fun onUserChange(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun onPasswordChange(value: String) { _state.value = _state.value.copy(password = value, error = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.baseUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "Preencha URL, usuário e senha")
            return
        }
        val normalized = s.baseUrl.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        _state.value = s.copy(isLoading = true, error = null, baseUrl = normalized)
        viewModelScope.launch {
            val result = repository.probeAndLogin(normalized, s.username.trim(), s.password)
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false)
                onSuccess()
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Falha ao conectar",
                )
            }
        }
    }
}
