package com.zbxapp.data.api

import com.zbxapp.data.storage.ServerConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ZabbixClient] using [MockWebServer]. These tests are guard rails
 * against two production regressions:
 *  1. Dropping `jsonrpc: "2.0"` from the request body (encodeDefaults = false).
 *  2. Failing to decode AcknowledgeResponse when Zabbix returns numeric eventids.
 *
 * Tests are written against the EXPECTED signatures for the other agent's pending
 * changes (acknowledgeEvent returns Unit, getProblems has includeSuppressed). If
 * those signatures are not yet in place they will surface as compile errors.
 */
class ZabbixClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ZabbixClient
    private val parseJson = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ZabbixClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun bearerServer(): ServerConfig =
        ServerConfig(baseUrl = server.url("/").toString().trimEnd('/'), useBearerAuth = true)

    private fun legacyServer(): ServerConfig =
        ServerConfig(baseUrl = server.url("/").toString().trimEnd('/'), useBearerAuth = false)

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json-rpc")
                .setBody(body),
        )
    }

    private fun RecordedRequest.bodyJson(): JsonObject =
        parseJson.parseToJsonElement(body.readUtf8()).jsonObject

    // ---------------------------------------------------------------------
    // getApiVersion — REGRESSION TEST for missing jsonrpc field
    // ---------------------------------------------------------------------

    @Test
    fun `getApiVersion sends jsonrpc 2_0 in body and returns result string`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":"7.0.4","id":1}""")

        val version = client.getApiVersion(bearerServer())

        assertEquals("7.0.4", version)

        val req = server.takeRequest()
        val body = req.bodyJson()
        // REGRESSION: encodeDefaults=false would drop this field; Zabbix rejects
        // with "parameter 'jsonrpc' is missing".
        assertEquals("2.0", body["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("apiinfo.version", body["method"]?.jsonPrimitive?.content)
        assertNotNull("id field must be present", body["id"])
        // apiinfo.version is an unauthenticated call: no auth field, no bearer header.
        assertNull(body["auth"])
        assertNull(req.getHeader("Authorization"))
    }

    // ---------------------------------------------------------------------
    // login
    // ---------------------------------------------------------------------

    @Test
    fun `login sends username and password and returns token`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":"abc123token","id":1}""")

        val token = client.login(bearerServer(), "alice", "s3cret")
        assertEquals("abc123token", token)

        val req = server.takeRequest()
        val body = req.bodyJson()
        assertEquals("2.0", body["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("user.login", body["method"]?.jsonPrimitive?.content)
        val params = body["params"]!!.jsonObject
        assertEquals("alice", params["username"]?.jsonPrimitive?.content)
        assertEquals("s3cret", params["password"]?.jsonPrimitive?.content)
        // user.login MUST NOT carry an auth token in body or header.
        assertNull(body["auth"])
        assertNull(req.getHeader("Authorization"))
    }

    // ---------------------------------------------------------------------
    // getProblems — bearer vs legacy auth wiring
    // ---------------------------------------------------------------------

    @Test
    fun `getProblems with bearer auth sends Authorization header and no auth in body`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":[],"id":1}""")

        client.getProblems(server = bearerServer(), token = "TOK", limit = 10)

        val req = server.takeRequest()
        assertEquals("Bearer TOK", req.getHeader("Authorization"))
        val body = req.bodyJson()
        assertEquals("2.0", body["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("problem.get", body["method"]?.jsonPrimitive?.content)
        assertNull("bearer mode must not embed auth in body", body["auth"])
    }

    @Test
    fun `getProblems with legacy auth embeds auth in body and no Authorization header`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":[],"id":1}""")

        client.getProblems(server = legacyServer(), token = "LEGACY_TOK", limit = 10)

        val req = server.takeRequest()
        assertNull("legacy mode must not set Authorization header", req.getHeader("Authorization"))
        val body = req.bodyJson()
        assertEquals("LEGACY_TOK", body["auth"]?.jsonPrimitive?.content)
    }

    @Test
    fun `getProblems parses a realistic problem_get response`() = runTest {
        // Sample modeled after a real Zabbix 7.0 problem.get reply with hosts,
        // acknowledges and tags arrays populated.
        val payload = """
            {
              "jsonrpc":"2.0",
              "result":[
                {
                  "eventid":"13887395",
                  "source":"0",
                  "object":"0",
                  "objectid":"13491",
                  "clock":"1715900000",
                  "ns":"123456",
                  "r_eventid":"0",
                  "r_clock":"0",
                  "r_ns":"0",
                  "correlationid":"0",
                  "userid":"0",
                  "name":"High CPU on web01",
                  "acknowledged":"1",
                  "severity":"4",
                  "opdata":"load=12.5",
                  "suppressed":"0",
                  "hosts":[{"hostid":"10101","host":"web01","name":"web01.prod"}],
                  "acknowledges":[
                    {"acknowledgeid":"55","userid":"1","message":"looking","clock":"1715900050",
                     "action":"6","old_severity":"3","new_severity":"4"}
                  ],
                  "tags":[
                    {"tag":"env","value":"prod"},
                    {"tag":"team","value":"sre"}
                  ]
                }
              ],
              "id":1
            }
        """.trimIndent()
        enqueueJson(payload)

        val problems = client.getProblems(server = bearerServer(), token = "TOK")

        assertEquals(1, problems.size)
        val p = problems.first()
        assertEquals("13887395", p.eventid)
        assertEquals("High CPU on web01", p.name)
        assertEquals("4", p.severity)
        assertEquals(1, p.hosts.size)
        assertEquals("web01.prod", p.hosts.first().name)
        assertEquals(1, p.acknowledges.size)
        assertEquals("looking", p.acknowledges.first().message)
        assertEquals(2, p.tags.size)
        assertEquals("env", p.tags[0].tag)
        assertEquals("prod", p.tags[0].value)
    }

    @Test
    fun `getProblems with includeSuppressed false adds suppressed false to params`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":[],"id":1}""")

        // Depends on the includeSuppressed flag added by the other agent; this is
        // already in ZabbixClient at the time of writing.
        client.getProblems(server = bearerServer(), token = "TOK", includeSuppressed = false)

        val req = server.takeRequest()
        val params = req.bodyJson()["params"]!!.jsonObject
        val suppressed = params["suppressed"]
        assertNotNull("suppressed param must be included when includeSuppressed=false", suppressed)
        assertFalse("suppressed must be the JSON boolean false", suppressed!!.jsonPrimitive.boolean)
    }

    @Test
    fun `getProblems with includeSuppressed true omits suppressed param`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":[],"id":1}""")

        client.getProblems(server = bearerServer(), token = "TOK", includeSuppressed = true)

        val req = server.takeRequest()
        val params = req.bodyJson()["params"]!!.jsonObject
        assertNull("suppressed must NOT be set when includeSuppressed=true", params["suppressed"])
    }

    // ---------------------------------------------------------------------
    // acknowledgeEvent — REGRESSION TEST for numeric eventids
    // ---------------------------------------------------------------------

    @Test
    fun `acknowledgeEvent does not throw when Zabbix returns numeric eventids`() = runTest {
        // REGRESSION: AcknowledgeResponse used to declare eventids as List<String>
        // which broke decoding because Zabbix returns numbers, e.g. [13887395].
        enqueueJson("""{"jsonrpc":"2.0","result":{"eventids":[13887395]},"id":1}""")

        // Should NOT throw. We don't care about return type — the other agent's
        // change makes this Unit-returning.
        client.acknowledgeEvent(
            server = bearerServer(),
            token = "TOK",
            eventId = "13887395",
            action = 6,
            message = "ack",
        )

        val req = server.takeRequest()
        val body = req.bodyJson()
        assertEquals("event.acknowledge", body["method"]?.jsonPrimitive?.content)
        val params = body["params"]!!.jsonObject
        assertEquals("13887395", params["eventids"]?.jsonPrimitive?.content)
        assertEquals(6, params["action"]?.jsonPrimitive?.content?.toInt())
        assertEquals("ack", params["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `acknowledgeEvent handles result with string eventids too`() = runTest {
        // Forward-compat: some older versions or proxies stringify eventids.
        enqueueJson("""{"jsonrpc":"2.0","result":{"eventids":["13887395"]},"id":1}""")

        client.acknowledgeEvent(
            server = bearerServer(),
            token = "TOK",
            eventId = "13887395",
            action = 2,
            message = null,
        )

        val req = server.takeRequest()
        val params = req.bodyJson()["params"]!!.jsonObject
        // message must be omitted when null (explicitNulls=false).
        assertNull(params["message"])
    }

    // ---------------------------------------------------------------------
    // JSON-RPC error envelope handling
    // ---------------------------------------------------------------------

    @Test
    fun `JSON-RPC error response is rethrown as ZabbixApiException with correct fields`() = runTest {
        enqueueJson(
            """
            {"jsonrpc":"2.0","error":{
                "code":-32602,
                "message":"Invalid params.",
                "data":"Session terminated, re-login, please."
            },"id":1}
            """.trimIndent(),
        )

        try {
            client.getProblems(server = bearerServer(), token = "TOK")
            fail("Expected ZabbixApiException to be thrown")
        } catch (e: ZabbixApiException) {
            assertEquals(-32602, e.zabbixCode)
            assertEquals("Invalid params.", e.zabbixMessage)
            assertEquals("Session terminated, re-login, please.", e.zabbixData)
            assertTrue(
                "exception message should include code and message",
                (e.message ?: "").contains("-32602") && (e.message ?: "").contains("Invalid params."),
            )
        }
    }

    @Test
    fun `JSON-RPC error without data is still thrown cleanly`() = runTest {
        enqueueJson(
            """{"jsonrpc":"2.0","error":{"code":-32500,"message":"Application error."},"id":1}""",
        )

        try {
            client.getApiVersion(bearerServer())
            fail("Expected ZabbixApiException to be thrown")
        } catch (e: ZabbixApiException) {
            assertEquals(-32500, e.zabbixCode)
            assertEquals("Application error.", e.zabbixMessage)
            assertNull(e.zabbixData)
        }
    }
}
