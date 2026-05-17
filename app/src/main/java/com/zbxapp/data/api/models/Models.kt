package com.zbxapp.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZbxHost(
    val hostid: String,
    val host: String = "",
    val name: String = "",
)

@Serializable
data class ZbxAcknowledge(
    val acknowledgeid: String = "",
    val userid: String = "",
    val message: String = "",
    val clock: String = "0",
    val action: String = "0",
    val old_severity: String = "0",
    val new_severity: String = "0",
)

@Serializable
data class ZbxTag(
    val tag: String = "",
    val value: String = "",
)

@Serializable
data class ZbxProblem(
    val eventid: String,
    val source: String = "0",
    @SerialName("object") val obj: String = "0",
    val objectid: String = "0",
    val clock: String = "0",
    val ns: String = "0",
    val r_eventid: String = "0",
    val r_clock: String = "0",
    val r_ns: String = "0",
    val correlationid: String = "0",
    val userid: String = "0",
    val name: String = "",
    val acknowledged: String = "0",
    val severity: String = "0",
    val opdata: String = "",
    val suppressed: String = "0",
    val hosts: List<ZbxHost> = emptyList(),
    val acknowledges: List<ZbxAcknowledge> = emptyList(),
    val tags: List<ZbxTag> = emptyList(),
)

@Serializable
data class ZbxTrigger(
    val triggerid: String,
    val description: String = "",
    val expression: String = "",
    val priority: String = "0",
    val status: String = "0",
    val value: String = "0",
    val lastchange: String = "0",
    val comments: String = "",
)

@Serializable
data class ZbxItem(
    val itemid: String,
    val name: String = "",
    val key_: String = "",
    val hostid: String = "0",
    val value_type: String = "0",
    val units: String = "",
    val lastvalue: String = "",
    val lastclock: String = "0",
    val description: String = "",
)

@Serializable
data class ZbxHistoryPoint(
    val itemid: String = "",
    val clock: String = "0",
    val value: String = "0",
    val ns: String = "0",
)

@Serializable
data class AcknowledgeResponse(
    val eventids: List<String> = emptyList(),
)
