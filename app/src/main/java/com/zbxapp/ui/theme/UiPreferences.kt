package com.zbxapp.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UiPrefsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val requireBiometric: Boolean = false,
)

object UiPreferences {
    private val _state = MutableStateFlow(UiPrefsState())
    val state: StateFlow<UiPrefsState> = _state

    fun setThemeMode(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setUseDynamicColor(value: Boolean) {
        _state.value = _state.value.copy(useDynamicColor = value)
    }

    fun setRequireBiometric(value: Boolean) {
        _state.value = _state.value.copy(requireBiometric = value)
    }
}
