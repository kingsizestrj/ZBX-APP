package com.zbxapp.data.cache

import android.util.Base64
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zbxapp.data.api.models.ZbxProblem
import com.zbxapp.data.api.models.ZbxTag

/**
 * Cached projection of [ZbxProblem] holding only the fields the list row and
 * detail header render. Tags are flattened to a single string so we don't need
 * a separate join table for the cache use case.
 */
@Entity(tableName = "problems")
data class ProblemEntity(
    @PrimaryKey val eventid: String,
    val name: String,
    val severity: String,
    val clock: Long,
    val acknowledged: String,
    val suppressed: String,
    val hostName: String,
    val opdata: String,
    /**
     * Flattened tags. Each tag is encoded as "base64(tag)=base64(value)" and
     * tags are joined with ";". Base64 ensures the separators ('=' and ';')
     * inside tag/value content cannot corrupt the round-trip.
     */
    val tagsFlat: String,
)

private const val TAGS_SEPARATOR = ";"
private const val TAG_KV_SEPARATOR = "="
private const val B64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE

fun ZbxProblem.toEntity(): ProblemEntity = ProblemEntity(
    eventid = eventid,
    name = name,
    severity = severity,
    clock = clock.toLongOrNull() ?: 0L,
    acknowledged = acknowledged,
    suppressed = suppressed,
    hostName = hosts.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: hosts.firstOrNull()?.host
        ?: "",
    opdata = opdata,
    tagsFlat = tags.joinToString(TAGS_SEPARATOR) { encodeTag(it) },
)

fun ProblemEntity.toDomain(): ZbxProblem = ZbxProblem(
    eventid = eventid,
    clock = clock.toString(),
    name = name,
    acknowledged = acknowledged,
    severity = severity,
    opdata = opdata,
    suppressed = suppressed,
    hosts = if (hostName.isBlank()) emptyList() else listOf(
        com.zbxapp.data.api.models.ZbxHost(hostid = "", host = hostName, name = hostName),
    ),
    tags = decodeTags(tagsFlat),
)

private fun b64Encode(value: String): String =
    Base64.encodeToString(value.toByteArray(Charsets.UTF_8), B64_FLAGS)

private fun b64Decode(value: String): String = try {
    String(Base64.decode(value, B64_FLAGS), Charsets.UTF_8)
} catch (_: IllegalArgumentException) {
    ""
}

private fun encodeTag(tag: ZbxTag): String =
    "${b64Encode(tag.tag)}$TAG_KV_SEPARATOR${b64Encode(tag.value)}"

private fun decodeTags(flat: String): List<ZbxTag> {
    if (flat.isBlank()) return emptyList()
    return flat.split(TAGS_SEPARATOR).mapNotNull { entry ->
        if (entry.isBlank()) return@mapNotNull null
        val idx = entry.indexOf(TAG_KV_SEPARATOR)
        if (idx < 0) ZbxTag(tag = b64Decode(entry), value = "")
        else ZbxTag(
            tag = b64Decode(entry.substring(0, idx)),
            value = b64Decode(entry.substring(idx + 1)),
        )
    }
}
