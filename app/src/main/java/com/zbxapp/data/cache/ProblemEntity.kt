package com.zbxapp.data.cache

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
    /** "tag1=v1;tag2=v2" — empty when no tags. */
    val tagsFlat: String,
)

private const val TAGS_SEPARATOR = ";"
private const val TAG_KV_SEPARATOR = "="

fun ZbxProblem.toEntity(): ProblemEntity = ProblemEntity(
    eventid = eventid,
    name = name,
    severity = severity,
    clock = clock.toLongOrNull() ?: 0L,
    acknowledged = acknowledged,
    suppressed = suppressed,
    hostName = hosts.firstOrNull()?.name ?: hosts.firstOrNull()?.host ?: "",
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

private fun encodeTag(tag: ZbxTag): String {
    val safeTag = tag.tag.replace(TAGS_SEPARATOR, " ").replace(TAG_KV_SEPARATOR, " ")
    val safeVal = tag.value.replace(TAGS_SEPARATOR, " ")
    return "$safeTag$TAG_KV_SEPARATOR$safeVal"
}

private fun decodeTags(flat: String): List<ZbxTag> {
    if (flat.isBlank()) return emptyList()
    return flat.split(TAGS_SEPARATOR).mapNotNull { entry ->
        if (entry.isBlank()) return@mapNotNull null
        val idx = entry.indexOf(TAG_KV_SEPARATOR)
        if (idx < 0) ZbxTag(tag = entry, value = "")
        else ZbxTag(tag = entry.substring(0, idx), value = entry.substring(idx + 1))
    }
}
