package com.forensiccybertech.eagleye.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OPENSEARCH = DateTimeFormatter.ofPattern("MMM d, yyyy @ HH:mm:ss.SSS", Locale.ENGLISH)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Accepts ISO-8601, epoch seconds/millis, and the "Nov 27, 2026 @ 08:42:18.810" format seen in OpenSearch exports. */
fun parseTimestamp(s: String): Instant? {
    if (s.isBlank()) return null
    runCatching { return Instant.parse(s) }
    runCatching { return OffsetDateTime.parse(s).toInstant() }
    runCatching { return LocalDateTime.parse(s, OPENSEARCH).atZone(ZoneId.systemDefault()).toInstant() }
    s.replace(",", "").toDoubleOrNull()?.let { return Instant.ofEpochMilli(if (it > 1e11) it.toLong() else (it * 1000).toLong()) }
    return null
}

fun agoOf(i: Instant, now: Instant = Instant.now()): String {
    val m = Duration.between(i, now).toMinutes()
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min${if (m == 1L) "" else "s"} ago"
        m < 60 * 24 -> (m / 60).let { "$it hour${if (it == 1L) "" else "s"} ago" }
        else -> (m / (60 * 24)).let { "$it day${if (it == 1L) "" else "s"} ago" }
    }
}

/** "2 mins ago"; falls back to the raw string when it can't be parsed. */
fun ago(ts: String): String = parseTimestamp(ts)?.let { agoOf(it) } ?: ts

fun ago(epochMs: Long): String = agoOf(Instant.ofEpochMilli(epochMs))

/** "09:40:12" in local time; falls back to the raw string. */
fun clock(ts: String): String = parseTimestamp(ts)?.atZone(ZoneId.systemDefault())?.format(CLOCK) ?: ts
