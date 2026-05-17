package com.zbxapp.ui.screens.login

import android.content.SharedPreferences
import com.zbxapp.data.api.ZabbixClient
import com.zbxapp.data.cache.ProblemDao
import com.zbxapp.data.cache.ProblemEntity
import com.zbxapp.data.repository.ZabbixRepository
import com.zbxapp.data.storage.AuthState
import com.zbxapp.data.storage.SecureStorage
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
class LoginViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: ZabbixRepository
    private lateinit var storage: SecureStorage
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        storage = LoginTestStorage.empty()
        repo = ZabbixRepository(ZabbixClient(), storage, InMemDao())
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

    @Test
    fun `submit blocks when any field is blank`() = runTest(dispatcher) {
        val vm = LoginViewModel(repo)
        vm.onUserChange("alice")
        vm.onPasswordChange("secret")
        // URL missing.
        var called = false
        vm.submit { called = true }
        advanceUntilIdle()

        assertFalse(called)
        assertNotNull(vm.state.value.error)
        // No network call should have been made.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `submit prepends https when URL has no scheme`() = runTest(dispatcher) {
        enqueueOk("""{"jsonrpc":"2.0","result":"7.0.0","id":1}""")
        enqueueOk("""{"jsonrpc":"2.0","result":"TOK","id":1}""")

        val vm = LoginViewModel(repo)
        // Note: the real base URL of the mock server starts with http://; we strip
        // the scheme manually to exercise the normalization branch.
        val rawHost = server.url("/").toString().removePrefix("http://").trimEnd('/')
        vm.onUrlChange(rawHost)
        vm.onUserChange("alice")
        vm.onPasswordChange("secret")

        var ok = false
        vm.submit { ok = true }
        advanceUntilIdle()

        // Probe + login should have hit the server even though the URL we set
        // omitted the scheme: the VM is expected to normalize to https://.
        // Since the mock server speaks plain HTTP, the requests will actually fail
        // with an SSL handshake error — which means the test can't assert success.
        // Instead: assert the normalized URL starts with https://.
        assertTrue(
            "baseUrl should be normalized to https://, got: ${vm.state.value.baseUrl}",
            vm.state.value.baseUrl.startsWith("https://"),
        )
    }

    @Test
    fun `submit preserves explicit http scheme`() = runTest(dispatcher) {
        enqueueOk("""{"jsonrpc":"2.0","result":"7.0.0","id":1}""")
        enqueueOk("""{"jsonrpc":"2.0","result":"TOK","id":1}""")

        val vm = LoginViewModel(repo)
        val url = server.url("/").toString().trimEnd('/') // already starts with http://
        vm.onUrlChange(url)
        vm.onUserChange("alice")
        vm.onPasswordChange("secret")

        var success = false
        vm.submit { success = true }
        advanceUntilIdle()

        assertTrue(
            "expected onSuccess to fire for valid http login flow",
            success,
        )
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertEquals(url, vm.state.value.baseUrl)
        // Both probe + login should have been sent.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `submit version detection uses bearer for major 7`() = runTest(dispatcher) {
        enqueueOk("""{"jsonrpc":"2.0","result":"7.2.1","id":1}""")
        enqueueOk("""{"jsonrpc":"2.0","result":"TOK","id":1}""")

        val vm = LoginViewModel(repo)
        vm.onUrlChange(server.url("/").toString().trimEnd('/'))
        vm.onUserChange("u")
        vm.onPasswordChange("p")
        vm.submit { }
        advanceUntilIdle()

        // user.login should have NOT carried Authorization header (login itself is unauth).
        val probe = server.takeRequest()
        val login = server.takeRequest()
        assertNull(probe.getHeader("Authorization"))
        assertNull(login.getHeader("Authorization"))
        // After login, storage should reflect bearer mode.
        assertEquals(true, storage.state.value.server?.useBearerAuth)
    }

    @Test
    fun `submit version detection uses legacy for pre-7`() = runTest(dispatcher) {
        enqueueOk("""{"jsonrpc":"2.0","result":"6.0.20","id":1}""")
        enqueueOk("""{"jsonrpc":"2.0","result":"TOK","id":1}""")

        val vm = LoginViewModel(repo)
        vm.onUrlChange(server.url("/").toString().trimEnd('/'))
        vm.onUserChange("u")
        vm.onPasswordChange("p")
        vm.submit { }
        advanceUntilIdle()

        assertEquals(false, storage.state.value.server?.useBearerAuth)
    }

    @Test
    fun `submit surfaces error message on failure`() = runTest(dispatcher) {
        enqueueOk(
            """{"jsonrpc":"2.0","error":{"code":-32602,"message":"Bad creds."},"id":1}""",
        )

        val vm = LoginViewModel(repo)
        vm.onUrlChange(server.url("/").toString().trimEnd('/'))
        vm.onUserChange("u")
        vm.onPasswordChange("p")
        var ok = false
        vm.submit { ok = true }
        advanceUntilIdle()

        assertFalse(ok)
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `field setters clear prior error`() {
        val vm = LoginViewModel(repo)
        // Trigger an error.
        vm.submit { }
        assertNotNull(vm.state.value.error)
        vm.onUrlChange("x")
        assertNull(vm.state.value.error)
    }
}

private class InMemDao : ProblemDao {
    @Volatile private var snap: List<ProblemEntity> = emptyList()
    private val flow = MutableStateFlow<List<ProblemEntity>>(emptyList())
    override fun observeAll(): Flow<List<ProblemEntity>> = flow
    override suspend fun getById(eventid: String): ProblemEntity? = snap.firstOrNull { it.eventid == eventid }
    override suspend fun deleteAll() { snap = emptyList(); flow.value = emptyList() }
    override suspend fun insertAll(items: List<ProblemEntity>) { snap = snap + items; flow.value = snap }
    override suspend fun replaceAll(items: List<ProblemEntity>) { snap = items; flow.value = items }
}

private object LoginTestStorage {
    fun empty(): SecureStorage {
        val cls = SecureStorage::class.java
        val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }.get(null) as sun.misc.Unsafe
        val instance = unsafe.allocateInstance(cls) as SecureStorage
        val prefs = Prefs()
        cls.getDeclaredField("prefs").apply { isAccessible = true }.set(instance, prefs)
        cls.getDeclaredField("_state").apply { isAccessible = true }
            .set(instance, MutableStateFlow(
                AuthState(
                    server = null, username = null, password = null, token = null,
                    minSeverity = 0, pollIntervalMinutes = 15, includeSuppressed = false,
                ),
            ))
        return instance
    }
}

private class Prefs : SharedPreferences {
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
    override fun edit(): SharedPreferences.Editor = Ed(this)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class Ed(val owner: Prefs) : SharedPreferences.Editor {
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
