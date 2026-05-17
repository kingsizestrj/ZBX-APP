package com.zbxapp.data.cache

import com.zbxapp.data.api.models.ZbxHost
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.api.models.ZbxTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests around the cache layer. Room itself (Sqlite) requires Robolectric or an
 * instrumentation test runner; since neither is in the unit-test classpath we
 * exercise the contract via a fake [ProblemDao] implementation that mirrors the
 * documented semantics (replaceAll = clear + insert in a single transaction,
 * observeAll emits the latest snapshot, getById returns null when absent).
 *
 * We ALSO test [ZbxProblem.toEntity] / [ProblemEntity.toDomain] round-tripping
 * since those are pure functions and easily covered here.
 */
class ProblemDaoTest {

    private fun dao() = FakeProblemDao()

    @Test
    fun `replaceAll atomically replaces previous rows`() = runTest {
        val dao = dao()
        dao.insertAll(
            listOf(
                entity("1", "old"),
                entity("2", "old2"),
            ),
        )
        assertEquals(2, dao.snapshot.size)

        dao.replaceAll(listOf(entity("3", "new")))

        assertEquals(1, dao.snapshot.size)
        assertEquals("3", dao.snapshot.first().eventid)
    }

    @Test
    fun `replaceAll with empty list clears the table`() = runTest {
        val dao = dao()
        dao.insertAll(listOf(entity("1", "x"), entity("2", "y")))
        dao.replaceAll(emptyList())
        assertEquals(0, dao.snapshot.size)
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `observeAll reflects the latest snapshot`() = runTest {
        val dao = dao()
        assertTrue(dao.observeAll().first().isEmpty())

        dao.insertAll(listOf(entity("1", "a")))
        val afterFirst = dao.observeAll().first()
        assertEquals(1, afterFirst.size)

        dao.replaceAll(listOf(entity("2", "b"), entity("3", "c")))
        val afterReplace = dao.observeAll().first()
        assertEquals(2, afterReplace.size)
        assertEquals(setOf("2", "3"), afterReplace.map { it.eventid }.toSet())
    }

    @Test
    fun `getById returns matching row or null`() = runTest {
        val dao = dao()
        dao.insertAll(listOf(entity("42", "answer")))
        assertEquals("answer", dao.getById("42")?.name)
        assertNull(dao.getById("999"))
    }

    @Test
    fun `deleteAll removes everything`() = runTest {
        val dao = dao()
        dao.insertAll(listOf(entity("1", "x"), entity("2", "y")))
        dao.deleteAll()
        assertEquals(0, dao.snapshot.size)
    }

    // ---------------------------------------------------------------------
    // entity <-> domain round-trip
    // ---------------------------------------------------------------------

    @Test
    fun `toEntity captures host name and flattens tags`() {
        val p = ZbxProblem(
            eventid = "10",
            name = "Disk space low",
            severity = "4",
            clock = "1700",
            acknowledged = "0",
            suppressed = "0",
            opdata = "free=2%",
            hosts = listOf(ZbxHost(hostid = "1", host = "h1", name = "host1.prod")),
            tags = listOf(ZbxTag(tag = "env", value = "prod"), ZbxTag(tag = "team", value = "sre")),
        )
        val e = p.toEntity()
        assertEquals("10", e.eventid)
        assertEquals("Disk space low", e.name)
        assertEquals("4", e.severity)
        assertEquals(1700L, e.clock)
        assertEquals("host1.prod", e.hostName)
        assertEquals("free=2%", e.opdata)
        assertEquals("env=prod;team=sre", e.tagsFlat)
    }

    @Test
    fun `toEntity prefers visible name then host id`() {
        val p1 = ZbxProblem(
            eventid = "1",
            hosts = listOf(ZbxHost(hostid = "1", host = "h", name = "Visible")),
        )
        assertEquals("Visible", p1.toEntity().hostName)

        val p2 = ZbxProblem(
            eventid = "2",
            hosts = listOf(ZbxHost(hostid = "1", host = "fallback", name = "")),
        )
        assertEquals("fallback", p2.toEntity().hostName)

        val p3 = ZbxProblem(eventid = "3", hosts = emptyList())
        assertEquals("", p3.toEntity().hostName)
    }

    @Test
    fun `toEntity coerces non-numeric clock to zero`() {
        val p = ZbxProblem(eventid = "1", clock = "notANumber")
        assertEquals(0L, p.toEntity().clock)
    }

    @Test
    fun `toDomain reconstructs problem with a single host`() {
        val e = ProblemEntity(
            eventid = "20",
            name = "n",
            severity = "2",
            clock = 1800L,
            acknowledged = "1",
            suppressed = "0",
            hostName = "myhost",
            opdata = "x",
            tagsFlat = "k=v;solo",
        )
        val d = e.toDomain()
        assertEquals("20", d.eventid)
        assertEquals("1800", d.clock)
        assertEquals(1, d.hosts.size)
        assertEquals("myhost", d.hosts.first().name)
        assertEquals(2, d.tags.size)
        assertEquals("k", d.tags[0].tag)
        assertEquals("v", d.tags[0].value)
        assertEquals("solo", d.tags[1].tag)
        assertEquals("", d.tags[1].value)
    }

    @Test
    fun `toDomain with blank host produces empty host list`() {
        val e = ProblemEntity(
            eventid = "1", name = "", severity = "0", clock = 0L,
            acknowledged = "0", suppressed = "0", hostName = "  ",
            opdata = "", tagsFlat = "",
        )
        assertEquals(0, e.toDomain().hosts.size)
    }

    @Test
    fun `tags round-trip survives encoded separators`() {
        val p = ZbxProblem(
            eventid = "1",
            tags = listOf(ZbxTag(tag = "with;sep", value = "and=eq"), ZbxTag(tag = "k", value = "v")),
        )
        val round = p.toEntity().toDomain()
        // The producer sanitizes separators to spaces. Both tags should be present
        // and the second one should match exactly.
        assertEquals(2, round.tags.size)
        val k = round.tags.firstOrNull { it.tag == "k" }
        assertNotNull(k)
        assertEquals("v", k!!.value)
    }

    private fun entity(id: String, name: String) = ProblemEntity(
        eventid = id, name = name, severity = "0", clock = 0L,
        acknowledged = "0", suppressed = "0", hostName = "", opdata = "", tagsFlat = "",
    )
}

private class FakeProblemDao : ProblemDao {
    @Volatile var snapshot: List<ProblemEntity> = emptyList()
        private set
    private val flow = MutableStateFlow<List<ProblemEntity>>(emptyList())

    override fun observeAll(): Flow<List<ProblemEntity>> = flow

    override suspend fun getById(eventid: String): ProblemEntity? =
        snapshot.firstOrNull { it.eventid == eventid }

    override suspend fun deleteAll() {
        snapshot = emptyList()
        flow.value = emptyList()
    }

    override suspend fun insertAll(items: List<ProblemEntity>) {
        // Mirror Room REPLACE on conflict: replace by primary key.
        val byId = snapshot.associateBy { it.eventid }.toMutableMap()
        items.forEach { byId[it.eventid] = it }
        snapshot = byId.values.toList()
        flow.value = snapshot
    }

    override suspend fun replaceAll(items: List<ProblemEntity>) {
        snapshot = items
        flow.value = items
    }
}
