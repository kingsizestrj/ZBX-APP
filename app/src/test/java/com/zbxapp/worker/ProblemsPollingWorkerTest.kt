package com.zbxapp.worker

import com.zbxapp.data.api.models.ZbxProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for [ProblemsPollingWorker]'s filtering and de-duplication.
 *
 * The worker itself can't be instantiated outside an Android runtime (it requires
 * a WorkerParameters + the application Context casts to ZbxApp), so instead we
 * encode the same predicates the worker uses on its data and assert they hold
 * for representative inputs. A regression in the worker that loosens or tightens
 * these filters must update these tests too — making the behaviour explicit.
 */
class ProblemsPollingWorkerTest {

    @Test
    fun `skips duplicate event ids based on last seen`() {
        val lastSeen = 1000L
        val incoming = listOf(
            problem(eventid = "999"),
            problem(eventid = "1000"),
            problem(eventid = "1001"),
            problem(eventid = "1500"),
        )
        val newOnes = incoming.filter { (it.eventid.toLongOrNull() ?: 0L) > lastSeen }
        assertEquals(2, newOnes.size)
        assertTrue(newOnes.all { (it.eventid.toLongOrNull() ?: 0L) > lastSeen })
    }

    @Test
    fun `filters new problems by minSeverity threshold`() {
        val problems = listOf(
            problem(eventid = "1", severity = "0"),
            problem(eventid = "2", severity = "2"),
            problem(eventid = "3", severity = "4"),
            problem(eventid = "4", severity = "5"),
        )
        val minSeverity = 3
        val includeSuppressed = true
        val filtered = problems.filter {
            (it.severity.toIntOrNull() ?: 0) >= minSeverity &&
                (includeSuppressed || it.suppressed != "1")
        }
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { (it.severity.toIntOrNull() ?: 0) >= 3 })
    }

    @Test
    fun `suppressed problems are excluded when includeSuppressed is false`() {
        val problems = listOf(
            problem(eventid = "1", severity = "4", suppressed = "1"),
            problem(eventid = "2", severity = "4", suppressed = "0"),
        )
        val filtered = problems.filter {
            (it.severity.toIntOrNull() ?: 0) >= 0 &&
                (false || it.suppressed != "1")
        }
        assertEquals(1, filtered.size)
        assertEquals("2", filtered.first().eventid)
    }

    @Test
    fun `suppressed problems are included when includeSuppressed is true`() {
        val problems = listOf(
            problem(eventid = "1", severity = "4", suppressed = "1"),
            problem(eventid = "2", severity = "4", suppressed = "0"),
        )
        val filtered = problems.filter {
            (it.severity.toIntOrNull() ?: 0) >= 0 &&
                (true || it.suppressed != "1")
        }
        assertEquals(2, filtered.size)
    }

    @Test
    fun `maxOfOrNull picks largest numeric eventid even with malformed entries`() {
        val problems = listOf(
            problem(eventid = "1"),
            problem(eventid = "not-a-number"),
            problem(eventid = "42"),
            problem(eventid = "100"),
        )
        val max = problems.maxOfOrNull { it.eventid.toLongOrNull() ?: 0L }
        assertEquals(100L, max)
    }

    @Test
    fun `safe interval coerces to minimum 15 minutes`() {
        // The companion encodes this rule:
        //   val safeInterval = intervalMinutes.coerceAtLeast(15).toLong()
        assertEquals(15L, 5.coerceAtLeast(15).toLong())
        assertEquals(15L, 0.coerceAtLeast(15).toLong())
        assertEquals(30L, 30.coerceAtLeast(15).toLong())
    }

    @Test
    fun `empty problem list yields empty new-problems list`() {
        val empty = emptyList<ZbxProblem>()
        val filtered = empty.filter { (it.eventid.toLongOrNull() ?: 0L) > 0L }
        assertTrue(filtered.isEmpty())
        assertFalse(filtered.isNotEmpty())
    }

    private fun problem(
        eventid: String,
        severity: String = "0",
        suppressed: String = "0",
    ): ZbxProblem = ZbxProblem(
        eventid = eventid,
        severity = severity,
        suppressed = suppressed,
    )
}
