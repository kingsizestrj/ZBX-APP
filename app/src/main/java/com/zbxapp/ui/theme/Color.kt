package com.zbxapp.ui.theme

import androidx.compose.ui.graphics.Color

// Severity palette aligned with Zabbix defaults
object Severity {
    val NotClassified = Color(0xFF97AAB3)
    val Information = Color(0xFF7499FF)
    val Warning = Color(0xFFFFC859)
    val Average = Color(0xFFFFA059)
    val High = Color(0xFFE97659)
    val Disaster = Color(0xFFE45959)

    fun colorFor(severity: Int): Color = when (severity) {
        1 -> Information
        2 -> Warning
        3 -> Average
        4 -> High
        5 -> Disaster
        else -> NotClassified
    }

    fun labelFor(severity: Int): String = when (severity) {
        1 -> "Information"
        2 -> "Warning"
        3 -> "Average"
        4 -> "High"
        5 -> "Disaster"
        else -> "Not classified"
    }
}

val ZbxDarkSurface = Color(0xFF0F1419)
val ZbxDarkSurfaceVariant = Color(0xFF1A2129)
val ZbxOnSurface = Color(0xFFE6EAEE)
val ZbxOnSurfaceMuted = Color(0xFF8A95A0)
val ZbxPrimary = Color(0xFFE45959)
val ZbxOnPrimary = Color(0xFFFFFFFF)
val ZbxAccent = Color(0xFF7499FF)
