package com.zbxapp.data.repository

import com.zbxapp.data.api.ZabbixApiException
import com.zbxapp.data.api.ZabbixClient
import com.zbxapp.data.api.models.ZbxHistoryPoint
import com.zbxapp.data.api.models.ZbxItem
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.api.models.ZbxTrigger
import com.zbxapp.data.cache.ProblemDao
import com.zbxapp.data.cache.toDomain
import com.zbxapp.data.cache.toEntity
import com.zbxapp.data.storage.SecureStorage
import com.zbxapp.data.storage.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Façade over [ZabbixClient] and [SecureStorage]. Re-authenticates transparently
 * when the stored token is rejected (Zabbix codes -32602 / -32500 for invalid auth).
 *
 * Also write-through caches the latest fetched problems list into Room so the UI
 * can render the last-known state immediately on cold start / flaky network.
 */
class ZabbixRepository(
    private val client: ZabbixClient,
    private val storage: SecureStorage,
    private val problemDao: ProblemDao,
) {

    val authState: StateFlow<com.zbxapp.data.storage.AuthState> = storage.state

    private val refreshMutex = Mutex()

    suspend fun probeAndLogin(baseUrl: String, username: String, password: String): Result<Unit> = runCatchingCoop {
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
        try {
            client.logout(server, token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // best-effort: clear regardless
        }
        storage.clearAll()
    }

    suspend fun fetchProblems(
        minSeverity: Int? = null,
        limit: Int = 200,
        includeSuppressed: Boolean? = null,
    ): Result<List<ZbxProblem>> = call { server, token ->
        val state = storage.state.value
        val problems = client.getProblems(
            server = server,
            token = token,
            minSeverity = minSeverity ?: state.minSeverity,
            limit = limit,
            includeSuppressed = includeSuppressed ?: state.includeSuppressed,
        )
        // Write-through cache so the next cold start has something to show.
        problemDao.replaceAll(problems.map { it.toEntity() })
        problems
    }

    /**
     * Observe the cached problems list. UI can subscribe to this and render the
     * last-known list immediately, while [refreshProblems] runs in the background.
     */
    fun observeCachedProblems(): Flow<List<ZbxProblem>> =
        problemDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Convenience alias matching the read/write pattern in ProblemsViewModel. */
    suspend fun refreshProblems(
        minSeverity: Int? = null,
        limit: Int = 200,
        includeSuppressed: Boolean? = null,
    ): Result<List<ZbxProblem>> = fetchProblems(minSeverity, limit, includeSuppressed)

    suspend fun getProblem(eventId: String): Result<ZbxProblem?> = call { server, token ->
        client.getProblemByEventId(server, token, eventId)
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
    }

    /**
     * Run [block] with current server + token. If the call fails because the token is
     * invalid, re-login (using stored credentials) and retry once.
     */
    private suspend fun <T> call(block: suspend (ServerConfig, String) -> T): Result<T> = runCatchingCoop {
        val state = storage.state.value
        val server = state.server ?: error("Servidor não configurado")
        var token = state.token ?: refreshToken(server, expectedStaleToken = null)
        try {
            block(server, token)
        } catch (e: ZabbixApiException) {
            if (isAuthError(e)) {
                token = refreshToken(server, expectedStaleToken = token)
                block(server, token)
            } else throw e
        }
    }

    /**
     * Refresh the auth token. Concurrent callers wait on [refreshMutex]; if another
     * caller already refreshed the token while we waited (current token differs from
     * [expectedStaleToken]) we just return the current token to avoid stampedes.
     */
    private suspend fun refreshToken(
        server: ServerConfig,
        expectedStaleToken: String?,
    ): String = refreshMutex.withLock {
        val current = storage.state.value.token
        if (expectedStaleToken != null && current != null && current != expectedStaleToken) {
            return@withLock current
        }
        val s = storage.state.value
        val user = s.username ?: error("Credenciais ausentes")
        val pwd = s.password ?: error("Credenciais ausentes")
        val newToken = client.login(server, user, pwd)
        storage.saveToken(newToken)
        newToken
    }

    /** [runCatching] that does not swallow coroutine cancellation. */
    private suspend fun <T> runCatchingCoop(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun isAuthError(e: ZabbixApiException): Boolean {
        val msg = (e.zabbixMessage + " " + (e.zabbixData ?: "")).lowercase()
        return "not authori" in msg || "session terminated" in msg || "re-login" in msg ||
            "invalid token" in msg || "permission" in msg
    }
}
