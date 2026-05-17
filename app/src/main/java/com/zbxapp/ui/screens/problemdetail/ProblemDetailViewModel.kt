package com.zbxapp.ui.screens.problemdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zbxapp.data.api.models.ZbxItem
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.repository.ZabbixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProblemDetailUiState(
    val isLoading: Boolean = true,
    val problem: ZbxProblem? = null,
    val items: List<ZbxItem> = emptyList(),
    val actionInProgress: Boolean = false,
    val actionMessage: String? = null,
    val error: String? = null,
)

class ProblemDetailViewModel(
    private val repository: ZabbixRepository,
    private val eventId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ProblemDetailUiState())
    val state: StateFlow<ProblemDetailUiState> = _state

    init { load() }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val problemsRes = repository.fetchProblems(limit = 500)
            val problem = problemsRes.getOrNull()?.firstOrNull { it.eventid == eventId }
            if (problem == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = problemsRes.exceptionOrNull()?.message ?: "Problema não encontrado",
                )
                return@launch
            }
            val items = repository.fetchItemsForTrigger(problem.objectid).getOrDefault(emptyList())
            _state.value = _state.value.copy(
                isLoading = false,
                problem = problem,
                items = items,
            )
        }
    }

    fun acknowledge(message: String?) = act(action = 2 or if (!message.isNullOrBlank()) 4 else 0, message = message, success = "Reconhecido")
    fun closeProblem(message: String?) = act(action = 1 or 2 or if (!message.isNullOrBlank()) 4 else 0, message = message, success = "Encerrado")
    fun addMessage(message: String) {
        if (message.isBlank()) return
        act(action = 4, message = message, success = "Comentário enviado")
    }

    private fun act(action: Int, message: String?, success: String) {
        _state.value = _state.value.copy(actionInProgress = true, actionMessage = null)
        viewModelScope.launch {
            val res = repository.acknowledge(eventId, action, message?.takeIf { it.isNotBlank() })
            res.onSuccess {
                _state.value = _state.value.copy(actionInProgress = false, actionMessage = success)
                load()
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    actionMessage = e.message ?: "Falha ao executar ação",
                )
            }
        }
    }
}
