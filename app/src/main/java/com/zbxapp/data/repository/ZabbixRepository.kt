package com.zbxapp.data.repository

import com.zbxapp.data.api.ZabbixApiException
import com.zbxapp.data.api.ZabbixClient
import com.zbxapp.data.api.models.ZbxHistoryPoint
import com.zbxapp.data.api.models.ZbxItem
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.api.models.ZbxTrigger
import com.zbxapp.data.storage.SecureStorage
import com.zbxapp.data.storage.ServerConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Façade over [ZabbixClient] and [SecureStorage]. Re-authenticates transparently
 * when the stored token is rejected (Zabbix codes -32602 / -32500 for invalid auth).
 */
class ZabbixRepository(
    private val client: ZabbixClient,
    private val storage: SecureStorage,
) {

    val authState: StateFlow<com.zbxapp.data.storage.AuthState> = storage.state

    suspend fun probeAndLogin(baseUrl: String, username: String, password: String): Result<Unit> = runCatching {
        // First, detect version on a temporary config (assume bearer; apiinfo.version doesn't need auth anyway)
        val probeServer = ServerConfig(baseUrl, useBearerAuth = true)
        val version = client.getApiVersion(probeServer)
        val major = version.substringBefore('.').toIntOrNull() ?: 0
        val useBearer = major >= 7
        val server = ServerConfig(baseUrl, useBearerAuth = useBearer)

        val token = client.login(server, username, password)
        storage.saveServer(baseUrl, useBearer)
        storage.saveCredentials(username, password)
        storage.saveToken(token)
    }

    suspend fun logout() {
        val s = storage.state.value
        val server = s.server ?: return
        val token = s.token ?: return
        runCatching { client.logout(server, token) }
        storage.clearAll()
    }

    suspend fun fetchProblems(minSeverity: Int? = null, limit: Int = 200): Result<List<ZbxProblem>> = call { server, token ->
        client.getProblems(server, token, minSeverity ?: storage.state.value.minSeverity, limit)
    }

    suspend fun fetchTriggers(triggerIds: List<String>): Result<List<ZbxTrigger>> = call { server, token ->
        client.getTriggers(server, token, triggerIds)
    }

    suspend fun fetchItemsForTrigger(triggerId: String): Result<List<ZbxItem>> = call { server, token ->
        client.getItemsForTrigger(server, token, triggerId)
    }

    suspend fun fetchItem(itemId: String): Result<ZbxItem?> = call { server, token ->
        client.getItem(server, token, itemId)
    }

    suspend fun fetchHistory(itemId: String, valueType: Int, timeFrom: Long, limit: Int = 500): Result<List<ZbxHistoryPoint>> = call { server, token ->
        client.getHistory(server, token, itemId, valueType, timeFrom, limit)
    }

    /** action bitmask: 1=close, 2=ack, 4=add message */
    suspend fun acknowledge(eventId: String, action: Int, message: String? = null): Result<Unit> = call { server, token ->
        client.acknowledgeEvent(server, token, eventId, action, message)
        Unit
    }

    /**
     * Run [block] with current server + token. If the call fails because the token is
     * invalid, re-login (using stored credentials) and retry once.
     */
    private suspend fun <T> call(block: suspend (ServerConfig, String) -> T): Result<T> = runCatching {
        val state = storage.state.value
        val server = state.server ?: error("Servidor não configurado")
        var token = state.token ?: refreshToken()
        try {
            block(server, token)
        } catch (e: ZabbixApiException) {
            if (isAuthError(e)) {
                token = refreshToken()
                block(server, token)
            } else throw e
        }
    }

    private suspend fun refreshToken(): String {
        val s = storage.state.value
        val server = s.server ?: error("Servidor não configurado")
        val user = s.username ?: error("Credenciais ausentes")
        val pwd = s.password ?: error("Credenciais ausentes")
        val newToken = client.login(server, user, pwd)
        storage.saveToken(newToken)
        return newToken
    }

    private fun isAuthError(e: ZabbixApiException): Boolean {
        val msg = (e.zabbixMessage + " " + (e.zabbixData ?: "")).lowercase()
        return "not authori" in msg || "session terminated" in msg || "re-login" in msg ||
            "invalid token" in msg || "permission" in msg
    }
}
