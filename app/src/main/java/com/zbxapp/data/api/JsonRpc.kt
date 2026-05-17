package com.zbxapp.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement,
    val id: Int = 1,
    val auth: String? = null,
)

@Serializable
data class JsonRpcResponse<T>(
    val jsonrpc: String = "2.0",
    val result: T? = null,
    val error: JsonRpcError? = null,
    val id: Int = 1,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: String? = null,
)

class ZabbixApiException(
    val zabbixCode: Int,
    val zabbixMessage: String,
    val zabbixData: String?,
) : Exception("Zabbix API error $zabbixCode: $zabbixMessage${zabbixData?.let { " — $it" } ?: ""}")
