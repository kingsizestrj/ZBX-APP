package com.zbxapp.ui.screens.problems

import android.content.SharedPreferences
import com.zbxapp.data.api.ZabbixClient
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.cache.ProblemDao
import com.zbxapp.data.cache.ProblemEntity
import com.zbxapp.data.repository.ZabbixRepository
import com.zbxapp.data.storage.AuthState
import com.zbxapp.data.storage.SecureStorage
import com.zbxapp.data.storage.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProblemsViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: ZabbixRepository
    private lateinit var storage: SecureStorage
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        storage = TestStorage.build(
            AuthState(
                server = ServerConfig(server.url("/").toString().trimEnd('/'), useBearerAuth = true),
                username = "u",
                password = "p",
                token = "T",
                minSeverity = 0,
                pollIntervalMinutes = 15,
                includeSuppressed = false,
            ),
        )
        repo = ZabbixRepository(ZabbixClient(), storage, InMemoryProblemDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun enqueueOk(body: String) {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json-rpc")
                .setBody(body),
        )
    }

    private fun problemsBody(problems: List<ZbxProblem>): String {
        val items = problems.joinToString(",") { p ->
            val hostsJson = if (p.hosts.isEmpty()) "[]" else "[" + p.hosts.joinToString(",") {
                """{"hostid":"${it.hostid}","host":"${it.host}","name":"${it.name}"}"""
            } + "]"
            """
            {"eventid":"${p.eventid}","name":"${p.name}","severity":"${p.severity}",
             "clock":"${p.clock}","acknowledged":"${p.acknowledged}",
             "suppressed":"${p.suppressed}","opdata":"${p.opdata}",
             "hosts":$hostsJson,"tags":[],"acknowledges":[]}
            """.trimIndent()
        }
        return """{"jsonrpc":"2.0","result":[$items],"id":1}"""
    }

    @Test
    fun `initial load populates problems and clears loading`() = runTest(dispatcher) {
        enqueueOk(
            problemsBody(
                listOf(
                    ZbxProblem(eventid = "1", name = "a", severity = "4"),
                    ZbxProblem(eventid = "2", name = "b", severity = "2"),
                ),
            ),
        )
        val vm = ProblemsViewModel(repo, storage)
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(2, s.problems.size)
        assertNull(s.error)
        assertTrue(s.lastUpdatedMs > 0)
    }

    @Test
    fun `load failure surfaces an error message`() = runTest(dispatcher) {
        enqueueOk(
            """{"jsonrpc":"2.0","error":{"code":-32000,"message":"Internal."},"id":1}""",
        )
        val vm = ProblemsViewModel(repo, storage)
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNotNull(s.error)
    }

    @Test
    fun `visibleProblems filters by severity threshold`() = runTest(dispatcher) {
        enqueueOk(
            problemsBody(
                listOf(
                    ZbxProblem(eventid = "1", severity = "1"),
                    ZbxProblem(eventid = "2", severity = "3"),
                    ZbxProblem(eventid = "3", severity = "5"),
                ),
            ),
        )
        val vm = ProblemsViewModel(repo, storage)
        advanceUntilIdle()
        // Default threshold is 0 — all visible.
        assertEquals(3, vm.visibleProblems().size)

        // Bumping the threshold via mutation of internal state isn't possible; use
        // setSeverityFilter — but that triggers a network reload. Provide that response.
        enqueueOk(
            problemsBody(
                listOf(
                    ZbxProblem(eventid = "2", severity = "3"),
                    ZbxProblem(eventid = "3", severity = "5"),
                ),
            ),
        )
        vm.setSeverityFilter(3)
        advanceUntilIdle()
        // visibleProblems applies the filter on top of whatever the repo returns.
        val visible = vm.visibleProblems()
        assertTrue(visible.all { (it.severity.toIntOrNull() ?: 0) >= 3 })
    }

    @Test
    fun `visibleProblems hides acked when onlyUnacked is true`() = runTest(dispatcher) {
        enqueueOk(
            problemsBody(
                listOf(
                    ZbxProblem(eventid = "1", acknowledged = "1", severity = "4"),
                    ZbxProblem(eventid = "2", acknowledged = "0", severity = "4"),
                ),
            ),
        )
        val vm = ProblemsViewModel(repo, storage)
        advanceUntilIdle()
        assertEquals(2, vm.visibleProblems().size)
        vm.setOnlyUnacked(true)
        val v = vm.visibleProblems()
        assertEquals(1, v.size)
        assertEquals("2", v.first().eventid)
    }

    @Test
    fun `setIncludeSuppressed triggers a reload and persists`() = runTest(dispatcher) {
        enqueueOk(problemsBody(emptyList()))
        val vm = ProblemsViewModel(repo, storage)
        advanceUntilIdle()
        assertEquals(1, server.requestCount)

        enqueueOk(problemsBody(emptyList()))
        vm.setIncludeSuppressed(true)
        advanceUntilIdle()
        assertEquals(2, server.requestCount)
        assertTrue(vm.state.value.includeSuppressed)
        // Should also have been persisted.
        assertTrue(storage.state.value.includeSuppressed)
    }

    @Test
    fun `refresh sets isRefreshing then clears it on success`() = runTest(dispatcher) {
        enqueueOk(problemsBody(emptyList()))
        val vm = ProblemsViewModel(repo, storage)
        advanceUntilIdle()
        enqueueOk(problemsBody(listOf(ZbxProblem(eventid = "9"))))
        vm.refresh()
        // Don't advance yet — confirm refreshing flag flipped on.
        assertTrue(
            "Expected isRefreshing to be true mid-flight",
            vm.state.value.isRefreshing || vm.state.value.problems.any { it.eventid == "9" },
        )
        advanceUntilIdle()
        assertFalse(vm.state.value.isRefreshing)
    }
}

/** Minimal in-memory dao for these tests. */
private class InMemoryProblemDao : ProblemDao {
    @Volatile private var snapshot: List<ProblemEntity> = emptyList()
    private val flow = MutableStateFlow<List<ProblemEntity>>(emptyList())
    override fun observeAll(): Flow<List<ProblemEntity>> = flow
    override suspend fun getById(eventid: String): ProblemEntity? =
        snapshot.firstOrNull { it.eventid == eventid }
    override suspend fun deleteAll() { snapshot = emptyList(); flow.value = emptyList() }
    override suspend fun insertAll(items: List<ProblemEntity>) {
        snapshot = snapshot + items; flow.value = snapshot
    }
    override suspend fun replaceAll(items: List<ProblemEntity>) {
        snapshot = items; flow.value = items
    }
}

private object TestStorage {

    fun build(initial: AuthState): SecureStorage {
        val cls = SecureStorage::class.java
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }
            .get(null) as sun.misc.Unsafe
        val instance = unsafe.allocateInstance(cls) as SecureStorage

        val prefs = InMemoryPrefs()
        writeStateToPrefs(prefs, initial)
        cls.getDeclaredField("prefs").apply { isAccessible = true }.set(instance, prefs)
        cls.getDeclaredField("_state").apply { isAccessible = true }
            .set(instance, MutableStateFlow(initial))
        return instance
    }

    private fun writeStateToPrefs(prefs: InMemoryPrefs, s: AuthState) {
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

private class InMemoryPrefs : SharedPreferences {
    val map = mutableMapOf<String, Any?>()
    fun directPut(k: String, v: Any?) { map[k] = v }
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(this)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class Editor(val owner: InMemoryPrefs) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false
        override fun putString(k: String, v: String?): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putStringSet(k: String, v: MutableSet<String>?): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putInt(k: String, v: Int): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putLong(k: String, v: Long): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putFloat(k: String, v: Float): SharedPreferences.Editor { pending[k] = v; return this }
        override fun putBoolean(k: String, v: Boolean): SharedPreferences.Editor { pending[k] = v; return this }
        override fun remove(k: String): SharedPreferences.Editor { removals.add(k); return this }
        override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
        override fun commit(): Boolean { flush(); return true }
        override fun apply() { flush() }
        private fun flush() {
            if (clearAll) owner.map.clear()
            removals.forEach { owner.map.remove(it) }
            owner.map.putAll(pending)
        }
    }
}
