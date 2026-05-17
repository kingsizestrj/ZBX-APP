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
    val error: String? = null,
    val lastUpdatedMs: Long = 0L,
)

class ProblemsViewModel(
    private val repository: ZabbixRepository,
    private val storage: SecureStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProblemsUiState(minSeverityFilter = storage.state.value.minSeverity),
    )
    val state: StateFlow<ProblemsUiState> = _state

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun setSeverityFilter(severity: Int) {
        _state.value = _state.value.copy(minSeverityFilter = severity)
        storage.saveMinSeverity(severity)
        load(initial = false)
    }

    fun setOnlyUnacked(value: Boolean) {
        _state.value = _state.value.copy(onlyUnacked = value)
    }

    fun visibleProblems(): List<ZbxProblem> {
        val s = _state.value
        return s.problems.filter { p ->
            (p.severity.toIntOrNull() ?: 0) >= s.minSeverityFilter &&
                (!s.onlyUnacked || p.acknowledged != "1")
        }
    }

    private fun load(initial: Boolean) {
        _state.value = _state.value.copy(
            isLoading = initial,
            isRefreshing = !initial,
            error = null,
        )
        viewModelScope.launch {
            val result = repository.fetchProblems(minSeverity = _state.value.minSeverityFilter)
            result.onSuccess { problems ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    problems = problems,
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
