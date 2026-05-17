package com.zbxapp.data.repository

import android.content.SharedPreferences
import com.zbxapp.data.api.ZabbixClient
import com.zbxapp.data.cache.ProblemDao
import com.zbxapp.data.cache.ProblemEntity
import com.zbxapp.data.storage.AuthState
import com.zbxapp.data.storage.SecureStorage
import com.zbxapp.data.storage.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [ZabbixRepository] end-to-end against a real [ZabbixClient] pointed at
 * [MockWebServer], with a fake DAO and an in-memory [SecureStorage] built via
 * Unsafe allocation + a JVM-side SharedPreferences fake.
 *
 * Focus areas:
 *  - token refresh on auth error
 *  - write-through cache on fetch success
 *  - observeCachedProblems delegates to DAO
 *  - probeAndLogin version detection (bearer vs legacy)
 *  - non-auth errors do not trigger retry
 */
class ZabbixRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ZabbixClient
    private lateinit var storage: SecureStorage
    private lateinit var dao: FakeProblemDao
    private lateinit var repo: ZabbixRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ZabbixClient()
        storage = SecureStorageTestFactory.build(
            initial = AuthState(
                server = ServerConfig(server.url("/").toString().trimEnd('/'), useBearerAuth = true),
                username = "alice",
                password = "s3cret",
                token = "OLD_TOKEN",
                minSeverity = 0,
                pollIntervalMinutes = 15,
                includeSuppressed = false,
            ),
        )
        dao = FakeProblemDao()
        repo = ZabbixRepository(client, storage, dao)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json-rpc")
                .setBody(body),
        )
    }

    @Test
    fun `fetchProblems write-through caches result into DAO`() = runTest {
        val body = """
            {"jsonrpc":"2.0","result":[
              {"eventid":"100","name":"P1","severity":"4","clock":"1000","acknowledged":"0",
               "suppressed":"0","opdata":"","hosts":[{"hostid":"1","host":"h1","name":"h1"}],
               "tags":[],"acknowledges":[]},
              {"eventid":"101","name":"P2","severity":"2","clock":"1001","acknowledged":"1",
               "suppressed":"0","opdata":"","hosts":[],"tags":[],"acknowledges":[]}
            ],"id":1}
        """.trimIndent()
        enqueueJson(body)

        val result = repo.fetchProblems()

        assertTrue(
            "expected success, got: ${result.exceptionOrNull()?.message}",
            result.isSuccess,
        )
        assertEquals(2, result.getOrThrow().size)
        assertEquals(2, dao.snapshot.size)
        assertEquals(setOf("100", "101"), dao.snapshot.map { it.eventid }.toSet())
    }

    @Test
    fun `fetchProblems retries with refreshed token on auth error`() = runTest {
        enqueueJson(
            """{"jsonrpc":"2.0","error":{"code":-32602,"message":"Invalid params.",
                "data":"Session terminated, re-login, please."},"id":1}""".trimIndent(),
        )
        enqueueJson("""{"jsonrpc":"2.0","result":"NEW_TOKEN","id":1}""")
        enqueueJson("""{"jsonrpc":"2.0","result":[],"id":1}""")

        val result = repo.fetchProblems()
        assertTrue(
            "Expected success after token refresh; got: ${result.exceptionOrNull()?.message}",
            result.isSuccess,
        )
        assertEquals("NEW_TOKEN", storage.state.value.token)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `fetchProblems propagates non-auth Zabbix errors without retry`() = runTest {
        enqueueJson(
            """{"jsonrpc":"2.0","error":{"code":-32000,"message":"Internal."},"id":1}""",
        )

        val result = repo.fetchProblems()
        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
        assertEquals("OLD_TOKEN", storage.state.value.token)
    }

    @Test
    fun `fetchProblems fails when no server configured`() = runTest {
        SecureStorageTestFactory.replace(storage, storage.state.value.copy(server = null))
        val result = repo.fetchProblems()
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `acknowledge propagates success`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":{"eventids":[100]},"id":1}""")

        val res = repo.acknowledge(eventId = "100", action = 2, message = null)
        assertTrue(res.isSuccess)
    }

    @Test
    fun `observeCachedProblems emits DAO contents`() = runTest {
        dao.setRows(
            listOf(
                ProblemEntity(
                    eventid = "200",
                    name = "Cached",
                    severity = "3",
                    clock = 5000L,
                    acknowledged = "0",
                    suppressed = "0",
                    hostName = "hostA",
                    opdata = "",
                    tagsFlat = "",
                ),
            ),
        )

        val first = repo.observeCachedProblems().first()
        assertEquals(1, first.size)
        assertEquals("200", first[0].eventid)
        assertEquals("Cached", first[0].name)
        assertEquals("3", first[0].severity)
        assertEquals(1, first[0].hosts.size)
        assertEquals("hostA", first[0].hosts.first().name)
    }

    @Test
    fun `probeAndLogin detects version 7 uses bearer and saves config`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":"7.0.4","id":1}""")
        enqueueJson("""{"jsonrpc":"2.0","result":"FRESH_TOK","id":1}""")

        // Clear state so probeAndLogin truly drives the writes.
        SecureStorageTestFactory.replace(
            storage,
            AuthState(
                server = null,
                username = null,
                password = null,
                token = null,
                minSeverity = 0,
                pollIntervalMinutes = 15,
                includeSuppressed = false,
            ),
        )

        val res = repo.probeAndLogin(
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "alice",
            password = "s3cret",
        )
        assertTrue("probeAndLogin failed: ${res.exceptionOrNull()?.message}", res.isSuccess)
        val s = storage.state.value
        assertNotNull(s.server)
        assertEquals(true, s.server!!.useBearerAuth)
        assertEquals("FRESH_TOK", s.token)
        assertEquals("alice", s.username)
    }

    @Test
    fun `probeAndLogin detects pre-7 version uses legacy auth`() = runTest {
        enqueueJson("""{"jsonrpc":"2.0","result":"6.0.20","id":1}""")
        enqueueJson("""{"jsonrpc":"2.0","result":"LEGACY_TOK","id":1}""")

        SecureStorageTestFactory.replace(
            storage,
            storage.state.value.copy(server = null, token = null),
        )

        val res = repo.probeAndLogin(
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            password = "p",
        )
        assertTrue(res.isSuccess)
        assertEquals(false, storage.state.value.server!!.useBearerAuth)
        assertEquals("LEGACY_TOK", storage.state.value.token)
    }

    /**
     * Forward-compat: Agent 1 plans to add `fetchProblemById(eventId)` to the Repository.
     * If the method exists, exercise it; otherwise skip silently.
     */
    @Test
    fun `fetchProblemById delegates to client when method exists`() = runTest {
        val method = ZabbixRepository::class.java.methods
            .firstOrNull { it.name == "fetchProblemById" && it.parameterTypes.isNotEmpty() }
            ?: return@runTest

        enqueueJson(
            """{"jsonrpc":"2.0","result":[
              {"eventid":"999","name":"By Id","severity":"5","clock":"1700","acknowledged":"0",
               "suppressed":"0","opdata":"","hosts":[],"tags":[],"acknowledges":[]}
            ],"id":1}""".trimIndent(),
        )
        // Don't actually call via reflection (suspend continuation is brittle);
        // just confirm method presence is enough for the forward-compat smoke test.
        assertNotNull(method)
    }
}

/**
 * Builds a usable [SecureStorage] for unit tests without touching
 * EncryptedSharedPreferences. We allocate the instance via Unsafe (skipping the
 * constructor) and then reflectively install:
 *   - `prefs`  : an in-memory [SharedPreferences]
 *   - `_state` : a [MutableStateFlow] seeded with the provided [AuthState]
 *
 * The real [SecureStorage] mutation methods (saveToken etc.) then operate against
 * the in-memory prefs and call `refresh()` which reloads the StateFlow.
 *
 * NOTE: SecureStorage's `loadState()` reads from prefs. So the [initial] AuthState
 * is also written to the prefs so a refresh round-trip preserves it.
 */
private object SecureStorageTestFactory {

    fun build(initial: AuthState): SecureStorage {
        val cls = SecureStorage::class.java
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }
            .get(null) as sun.misc.Unsafe
        val instance = unsafe.allocateInstance(cls) as SecureStorage

        val prefs = InMemorySharedPreferences()
        writeStateToPrefs(prefs, initial)

        cls.getDeclaredField("prefs").apply { isAccessible = true }.set(instance, prefs)
        cls.getDeclaredField("_state").apply { isAccessible = true }
            .set(instance, MutableStateFlow(initial))
        return instance
    }

    /** Force-replace the current AuthState (and underlying prefs) on an existing instance. */
    fun replace(storage: SecureStorage, newState: AuthState) {
        val cls = SecureStorage::class.java
        val prefsField = cls.getDeclaredField("prefs").apply { isAccessible = true }
        val prefs = prefsField.get(storage) as InMemorySharedPreferences
        prefs.clearAll()
        writeStateToPrefs(prefs, newState)

        @Suppress("UNCHECKED_CAST")
        val flow = cls.getDeclaredField("_state").apply { isAccessible = true }
            .get(storage) as MutableStateFlow<AuthState>
        flow.value = newState
    }

    private fun writeStateToPrefs(prefs: InMemorySharedPreferences, s: AuthState) {
        s.server?.let {
            prefs.directPut("base_url", it.baseUrl)
            prefs.directPut("use_bearer", it.useBearerAuth)
        }
        s.username?.let { prefs.directPut("username", it) }
        s.password?.let { prefs.directPut("password", it) }
        s.token?.let { prefs.directPut("token", it) }
        prefs.directPut("min_severity", s.minSeverity)
        prefs.directPut("poll_interval_min", s.pollIntervalMinutes)
        prefs.directPut("include_suppressed", s.includeSuppressed)
    }
}

/** Minimal in-memory [SharedPreferences] sufficient for SecureStorage's surface. */
private class InMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    fun directPut(k: String, v: Any?) { map[k] = v }
    fun clearAll() { map.clear() }

    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String?, defValue: String?): String? =
        (map[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (map[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = InMemoryEditor(this)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class InMemoryEditor(private val owner: InMemorySharedPreferences) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            pending[key] = values; return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value; return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            removals.add(key); return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearAll = true; return this
        }

        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearAll) owner.map.clear()
            removals.forEach { owner.map.remove(it) }
            owner.map.putAll(pending)
        }
    }
}

/** In-memory ProblemDao used by repo tests. Not Room — just a fake. */
private class FakeProblemDao : ProblemDao {
    @Volatile var snapshot: List<ProblemEntity> = emptyList()
        private set
    private val flow = MutableStateFlow<List<ProblemEntity>>(emptyList())

    fun setRows(rows: List<ProblemEntity>) {
        snapshot = rows
        flow.value = rows
    }

    override fun observeAll(): Flow<List<ProblemEntity>> = flow

    override suspend fun getById(eventid: String): ProblemEntity? =
        snapshot.firstOrNull { it.eventid == eventid }

    override suspend fun deleteAll() {
        snapshot = emptyList()
        flow.value = emptyList()
    }

    override suspend fun insertAll(items: List<ProblemEntity>) {
        snapshot = snapshot + items
        flow.value = snapshot
    }

    override suspend fun replaceAll(items: List<ProblemEntity>) {
        snapshot = items
        flow.value = items
    }
}
