package com.zbxapp.ui.screens.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zbxapp.data.api.models.ZbxHistoryPoint
import com.zbxapp.data.api.models.ZbxItem
import com.zbxapp.data.repository.ZabbixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class TimeRange(val seconds: Long, val label: String) {
    LAST_HOUR(3_600L, "1h"),
    LAST_6H(6 * 3_600L, "6h"),
    LAST_24H(24 * 3_600L, "24h"),
    LAST_7D(7 * 24 * 3_600L, "7d"),
}

data class GraphUiState(
    val isLoading: Boolean = true,
    val item: ZbxItem? = null,
    val history: List<ZbxHistoryPoint> = emptyList(),
    val range: TimeRange = TimeRange.LAST_HOUR,
    val error: String? = null,
)

class GraphViewModel(
    private val repository: ZabbixRepository,
    private val itemId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(GraphUiState())
    val state: StateFlow<GraphUiState> = _state

    init { load() }

    fun setRange(range: TimeRange) {
        _state.value = _state.value.copy(range = range)
        load()
    }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val itemRes = repository.fetchItem(itemId)
            val item = itemRes.getOrNull()
            if (item == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = itemRes.exceptionOrNull()?.message ?: "Item não encontrado",
                )
                return@launch
            }
            val valueType = item.value_type.toIntOrNull() ?: 0
            // history.get only works on numeric value_types (0=float, 3=uint)
            if (valueType != 0 && valueType != 3) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    item = item,
                    history = emptyList(),
                    error = "Este item não é numérico — sem gráfico disponível.",
                )
                return@launch
            }
            val from = System.currentTimeMillis() / 1000 - _state.value.range.seconds
            val histRes = repository.fetchHistory(itemId, valueType, from)
            histRes.onSuccess { points ->
                _state.value = _state.value.copy(isLoading = false, item = item, history = points)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    item = item,
                    error = e.message ?: "Falha ao carregar histórico",
                )
            }
        }
    }
}
