package com.zbxapp.ui.screens.problemdetail

import android.content.SharedPreferences
import com.zbxapp.data.api.ZabbixClient
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
class ProblemDetailViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: ZabbixRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        val storage = TestSecureStorage.build(
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
        repo = ZabbixRepository(ZabbixClient(), storage, InMemoryDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun enqueueOk(body: String) {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json-rpc").setBody(body),
        )
    }

    private val sampleProblemsBody = """
        {"jsonrpc":"2.0","result":[
          {"eventid":"42","name":"Disco cheio","severity":"4","clock":"100",
           "acknowledged":"0","suppressed":"0","opdata":"",
           "hosts":[{"hostid":"1","host":"h","name":"h"}],
           "tags":[],"acknowledges":[],"objectid":"5555"}
        ],"id":1}
    """.trimIndent()

    private val itemsBody = """{"jsonrpc":"2.0","result":[
        {"itemid":"7","name":"item","key_":"k","value_type":"3"}
    ],"id":1}"""

    @Test
    fun `init load finds problem and fetches items for trigger`() = runTest(dispatcher) {
        enqueueOk(sampleProblemsBody)
        enqueueOk(itemsBody)

        val vm = ProblemDetailViewModel(repo, eventId = "42")
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNotNull(s.problem)
        assertEquals("42", s.problem?.eventid)
        assertEquals(1, s.items.size)
        assertEquals("item", s.items.first().name)
        assertNull(s.error)
    }

    @Test
    fun `load surfaces error when problem not in list`() = runTest(dispatcher) {
        // Empty list returned.
        enqueueOk("""{"jsonrpc":"2.0","result":[],"id":1}""")

        val vm = ProblemDetailViewModel(repo, eventId = "missing")
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNull(s.problem)
        assertNotNull(s.error)
    }

    @Test
    fun `load surfaces error when repository fails`() = runTest(dispatcher) {
        enqueueOk(
            """{"jsonrpc":"2.0","error":{"code":-32000,"message":"Boom."},"id":1}""",
        )
        val vm = ProblemDetailViewModel(repo, eventId = "42")
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNotNull(s.error)
    }

    @Test
    fun `acknowledge issues event_acknowledge and triggers reload`() = runTest(dispatcher) {
        // init load:
        enqueueOk(sampleProblemsBody)
        enqueueOk(itemsBody)
        // acknowledge:
        enqueueOk("""{"jsonrpc":"2.0","result":{"eventids":[42]},"id":1}""")
        // reload after success:
        enqueueOk(sampleProblemsBody)
        enqueueOk(itemsBody)

        val vm = ProblemDetailViewModel(repo, eventId = "42")
        advanceUntilIdle()
        assertEquals(2, server.requestCount)

        vm.acknowledge(message = "looking into it")
        advanceUntilIdle()

        // 2 init + 1 ack + 2 reload = 5
        assertEquals(5, server.requestCount)
        val s = vm.state.value
        assertFalse(s.actionInProgress)
        assertEquals("Reconhecido", s.actionMessage)
    }

    @Test
    fun `acknowledge surfaces failure message`() = runTest(dispatcher) {
        enqueueOk(sampleProblemsBody)
        enqueueOk(itemsBody)
        enqueueOk(
            """{"jsonrpc":"2.0","error":{"code":-32000,"message":"Failed."},"id":1}""",
        )

        val vm = ProblemDetailViewModel(repo, eventId = "42")
        advanceUntilIdle()
        vm.acknowledge(message = null)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.actionInProgress)
        assertNotNull(s.actionMessage)
        assertTrue(
            "expected error-ish action message, got: ${s.actionMessage}",
            !s.actionMessage.isNullOrBlank(),
        )
    }

    @Test
    fun `closeProblem includes close bit`() = runTest(dispatcher) {
        enqueueOk(sampleProblemsBody)
        enqueueOk(itemsBody)
        enqueueOk("""{"jsonrpc":"2.0","result":{"eventids":[42]},"id":1}""")
        enqueueOk(sampleProblemsBody)
        enqueueOk(itemsBody)

        val vm = ProblemDetailViewModel(repo, eventId = "42")
        advanceUntilIdle()
        vm.closeProblem(message = null)
        advanceUntilIdle()

        // Check the ack request body has action containing bit 1.
        // Drain first two init requests.
        server.takeRequest(); server.takeRequest()
        val ackReq = server.takeRequest()
        val body = ackReq.body.readUtf8()
        // Action is 1|2 = 3 (no message bit since msg is null/blank).
        assertTrue("ack action should include close bit: $body", body.contains("\"action\":3"))
    }

    @Test
    fun `addMessage with blank input is a no-op`() = runTest(dispatcher) {
        enqueueOk(sampleProblemsBody)
        enqueueOk(itemsBody)

        val vm = ProblemDetailViewModel(repo, eventId = "42")
        advanceUntilIdle()
        val before = server.requestCount

        vm.addMessage("   ")
        advanceUntilIdle()

        assertEquals(
            "blank message must not trigger a network call",
            before,
            server.requestCount,
        )
    }
}

private class InMemoryDao : ProblemDao {
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

private object TestSecureStorage {
    fun build(initial: AuthState): SecureStorage {
        val cls = SecureStorage::class.java
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }.get(null) as sun.misc.Unsafe
        val instance = unsafe.allocateInstance(cls) as SecureStorage
        val prefs = InMemPrefs()
        cls.getDeclaredField("prefs").apply { isAccessible = true }.set(instance, prefs)
        cls.getDeclaredField("_state").apply { isAccessible = true }
            .set(instance, MutableStateFlow(initial))
        return instance
    }
}

private class InMemPrefs : SharedPreferences {
    val map = mutableMapOf<String, Any?>()
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
    override fun edit(): SharedPreferences.Editor = E(this)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class E(val owner: InMemPrefs) : SharedPreferences.Editor {
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
