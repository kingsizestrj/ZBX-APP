package com.zbxapp.data.api

import com.zbxapp.data.api.models.ZbxHistoryPoint
import com.zbxapp.data.api.models.ZbxItem
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.api.models.ZbxTrigger
import com.zbxapp.data.storage.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Low-level Zabbix JSON-RPC client. Handles both legacy auth (auth field in body,
 * pre-7.0) and bearer auth (Authorization header, 7.0+).
 */
class ZabbixClient(
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson,
) {

    suspend fun getApiVersion(server: ServerConfig): String =
        callPrimitive(server, "apiinfo.version", buildJsonArray { }, authToken = null)

    suspend fun login(server: ServerConfig, username: String, password: String): String {
        val params = buildJsonObject {
            put("username", JsonPrimitive(username))
            put("password", JsonPrimitive(password))
        }
        return callPrimitive(server, "user.login", params, authToken = null)
    }

    suspend fun logout(server: ServerConfig, token: String): Boolean = try {
        callPrimitive(server, "user.logout", buildJsonArray { }, authToken = token)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        false
    }

    suspend fun getProblems(
        server: ServerConfig,
        token: String,
        minSeverity: Int = 0,
        limit: Int = 200,
        sinceClock: Long? = null,
        includeSuppressed: Boolean = false,
    ): List<ZbxProblem> {
        val params = buildJsonObject {
            put("output", JsonPrimitive("extend"))
            put("selectAcknowledges", JsonPrimitive("extend"))
            put("selectHosts", JsonPrimitive("extend"))
            put("selectTags", JsonPrimitive("extend"))
            put("recent", JsonPrimitive(false))
            put("sortfield", buildJsonArray { add(JsonPrimitive("eventid")) })
            put("sortorder", JsonPrimitive("DESC"))
            put("limit", JsonPrimitive(limit))
            if (minSeverity > 0) {
                put("severities", buildJsonArray {
                    for (s in minSeverity..5) add(JsonPrimitive(s))
                })
            }
            if (!includeSuppressed) put("suppressed", JsonPrimitive(false))
            if (sinceClock != null) put("time_from", JsonPrimitive(sinceClock))
        }
        return call(server, "problem.get", params, token, ListSerializer(ZbxProblem.serializer()))
    }

    suspend fun getProblemByEventId(
        server: ServerConfig,
        token: String,
        eventId: String,
    ): ZbxProblem? {
        val params = buildJsonObject {
            put("output", JsonPrimitive("extend"))
            put("selectAcknowledges", JsonPrimitive("extend"))
            put("selectHosts", JsonPrimitive("extend"))
            put("selectTags", JsonPrimitive("extend"))
            put("recent", JsonPrimitive(false))
            put("eventids", buildJsonArray { add(JsonPrimitive(eventId)) })
            put("limit", JsonPrimitive(1))
        }
        val list = call(server, "problem.get", params, token, ListSerializer(ZbxProblem.serializer()))
        return list.firstOrNull()
    }

    suspend fun getTriggers(server: ServerConfig, token: String, triggerIds: List<String>): List<ZbxTrigger> {
        if (triggerIds.isEmpty()) return emptyList()
        val params = buildJsonObject {
            put("output", JsonPrimitive("extend"))
            put("triggerids", buildJsonArray { triggerIds.forEach { add(JsonPrimitive(it)) } })
        }
        return call(server, "trigger.get", params, token, ListSerializer(ZbxTrigger.serializer()))
    }

    suspend fun getItemsForTrigger(server: ServerConfig, token: String, triggerId: String): List<ZbxItem> {
        val params = buildJsonObject {
            put("output", JsonPrimitive("extend"))
            put("triggerids", buildJsonArray { add(JsonPrimitive(triggerId)) })
        }
        return call(server, "item.get", params, token, ListSerializer(ZbxItem.serializer()))
    }

    suspend fun getItem(server: ServerConfig, token: String, itemId: String): ZbxItem? {
        val params = buildJsonObject {
            put("output", JsonPrimitive("extend"))
            put("itemids", buildJsonArray { add(JsonPrimitive(itemId)) })
        }
        val list = call(server, "item.get", params, token, ListSerializer(ZbxItem.serializer()))
        return list.firstOrNull()
    }

    suspend fun getHistory(
        server: ServerConfig,
        token: String,
        itemId: String,
        valueType: Int,
        timeFrom: Long,
        limit: Int = 500,
    ): List<ZbxHistoryPoint> {
        val params = buildJsonObject {
            put("output", JsonPrimitive("extend"))
            put("history", JsonPrimitive(valueType))
            put("itemids", buildJsonArray { add(JsonPrimitive(itemId)) })
            put("time_from", JsonPrimitive(timeFrom))
            put("sortfield", JsonPrimitive("clock"))
            put("sortorder", JsonPrimitive("ASC"))
            put("limit", JsonPrimitive(limit))
        }
        return call(server, "history.get", params, token, ListSerializer(ZbxHistoryPoint.serializer()))
    }

    /**
     * Acknowledge action bitmask:
     *  1=close, 2=ack, 4=add message, 8=change severity, 16=unack, 32=suppress
     */
    suspend fun acknowledgeEvent(
        server: ServerConfig,
        token: String,
        eventId: String,
        action: Int,
        message: String? = null,
    ) {
        val params = buildJsonObject {
            put("eventids", JsonPrimitive(eventId))
            put("action", JsonPrimitive(action))
            if (message != null) put("message", JsonPrimitive(message))
        }
        // We don't consume the returned eventids; parseEnvelope surfaces any JSON-RPC error.
        parseEnvelope(rawCall(server, "event.acknowledge", params, token))
    }

    private suspend fun <T> call(
        server: ServerConfig,
        method: String,
        params: JsonElement,
        authToken: String?,
        deserializer: KSerializer<T>,
    ): T {
        val resp = parseEnvelope(rawCall(server, method, params, authToken))
        val result = resp["result"] ?: throw IllegalStateException("Missing result")
        return json.decodeFromJsonElement(deserializer, result)
    }

    private suspend fun callPrimitive(
        server: ServerConfig,
        method: String,
        params: JsonElement,
        authToken: String?,
    ): String {
        val resp = parseEnvelope(rawCall(server, method, params, authToken))
        val result = resp["result"] ?: throw IllegalStateException("Missing result")
        return result.jsonPrimitive.content
    }

    private fun parseEnvelope(raw: String): JsonObject {
        val obj = json.parseToJsonElement(raw).jsonObject
        obj["error"]?.let { errorEl ->
            val err = json.decodeFromJsonElement(JsonRpcError.serializer(), errorEl)
            throw ZabbixApiException(err.code, err.message, err.data)
        }
        return obj
    }

    private suspend fun rawCall(
        server: ServerConfig,
        method: String,
        params: JsonElement,
        authToken: String?,
    ): String = withContext(Dispatchers.IO) {
        val noAuthMethods = setOf("apiinfo.version", "user.login", "user.checkAuthentication")
        val needsAuth = authToken != null && method !in noAuthMethods
        val useBearer = needsAuth && server.useBearerAuth
        val embedAuth = needsAuth && !server.useBearerAuth

        val bodyJson = JsonRpcRequest(
            method = method,
            params = params,
            auth = if (embedAuth) authToken else null,
        )
        val bodyStr = json.encodeToString(JsonRpcRequest.serializer(), bodyJson)

        val mediaType = "application/json-rpc; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url(server.apiUrl)
            .post(bodyStr.toRequestBody(mediaType))
            .apply {
                if (useBearer && authToken != null) addHeader("Authorization", "Bearer $authToken")
            }
            .build()

        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${text.take(200)}")
            }
            text
        }
    }

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
