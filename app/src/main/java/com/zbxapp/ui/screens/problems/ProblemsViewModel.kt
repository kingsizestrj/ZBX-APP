package com.zbxapp.ui.screens.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.repository.ZabbixRepository
import com.zbxapp.data.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProblemsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val problems: List<ZbxProblem> = emptyList(),
    val minSeverityFilter: Int = 0,
    val onlyUnacked: Boolean = false,
    val includeSuppressed: Boolean = false,
    val error: String? = null,
    val lastUpdatedMs: Long = 0L,
)

class ProblemsViewModel(
    private val repository: ZabbixRepository,
    private val storage: SecureStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProblemsUiState(
            minSeverityFilter = storage.state.value.minSeverity,
            includeSuppressed = storage.state.value.includeSuppressed,
        ),
    )
    val state: StateFlow<ProblemsUiState> = _state

    init {
        observeCache()
        refresh(initial = true)
    }

    fun refresh() = refresh(initial = false)

    fun setSeverityFilter(severity: Int) {
        _state.value = _state.value.copy(minSeverityFilter = severity)
        storage.saveMinSeverity(severity)
        refresh(initial = false)
    }

    fun setOnlyUnacked(value: Boolean) {
        _state.value = _state.value.copy(onlyUnacked = value)
    }

    fun setIncludeSuppressed(value: Boolean) {
        _state.value = _state.value.copy(includeSuppressed = value)
        storage.saveIncludeSuppressed(value)
        refresh(initial = false)
    }

    fun visibleProblems(): List<ZbxProblem> {
        val s = _state.value
        return s.problems.filter { p ->
            (p.severity.toIntOrNull() ?: 0) >= s.minSeverityFilter &&
                (!s.onlyUnacked || p.acknowledged != "1")
        }
    }

    private fun observeCache() {
        viewModelScope.launch {
            repository.observeCachedProblems().collect { cached ->
                _state.value = _state.value.copy(problems = cached)
            }
        }
    }

    private fun refresh(initial: Boolean) {
        _state.value = _state.value.copy(
            isLoading = initial && _state.value.problems.isEmpty(),
            isRefreshing = !initial,
            error = null,
        )
        viewModelScope.launch {
            val result = repository.refreshProblems(
                minSeverity = _state.value.minSeverityFilter,
                includeSuppressed = _state.value.includeSuppressed,
            )
            result.onSuccess {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    lastUpdatedMs = System.currentTimeMillis(),
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Falha ao buscar problemas",
                )
            }
        }
    }
}
