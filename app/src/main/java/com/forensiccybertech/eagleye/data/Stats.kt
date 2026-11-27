package com.forensiccybertech.eagleye.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class NameCount(val name: String, val count: Int, val sub: String = "")

data class Summary(
    val totalAlerts: Int, val changePct: Double?,
    val today: Map<String, Int>,
    val agentsTotal: Int, val online: Int, val offline: Int, val totalSeats: Int?, val remainingSeats: Int?,
    val actionStatus: Map<String, Int>, val actionCounts: Map<String, Int>, val byCategory: Map<String, Int>,
    val topAgents: List<NameCount>,
)

data class TopStats(
    val period: String, val rules: List<NameCount>, val agents: List<NameCount>, val tactics: List<NameCount>,
    val techniques: List<NameCount>, val users: List<NameCount>, val tags: List<NameCount>,
)

data class Bucket(val epochMs: Long, val critical: Int, val high: Int, val medium: Int, val low: Int)

data class Host(
    val id: String, val hostname: String, val online: Boolean?, val lastSeenMs: Long?, val user: String,
    val type: String, val os: String, val ip: String, val agentVersion: String, val isolated: Boolean,
)

data class HostsResult(val hosts: List<Host>, val total: Int, val totalSeats: Int?, val remainingSeats: Int?)

/** Parsers for the console's stats/hosts endpoints. Missing or oddly-typed fields degrade to 0/"" instead of failing. */
object StatsParser {
    private val json = Json { isLenient = true }

    private fun root(body: String) = json.parseToJsonElement(body) as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.int(): Int = (this as? JsonPrimitive)?.contentOrNull?.replace(",", "")?.toDoubleOrNull()?.toInt() ?: 0
    private fun JsonElement?.intOrNull(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.replace(",", "")?.toDoubleOrNull()?.toInt()
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toDoubleOrNull()
    private fun JsonElement?.str(): String = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()
    private fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray) ?: emptyList()
    private fun JsonObject.ints(vararg keys: String) = keys.associateWith { this[it].int() }

    fun summary(body: String): Summary {
        val r = root(body); val a = r["alerts"].obj(); val ag = r["agents"].obj()
        return Summary(
            totalAlerts = a["total"].int(), changePct = a["last_7d"].obj()["change_pct"].dbl(),
            today = a["today"].obj()["current"].obj().ints("critical", "high", "medium", "low"),
            agentsTotal = ag["total"].int(), online = ag["online"].int(), offline = ag["offline"].int(),
            totalSeats = ag["total_seats"].intOrNull(), remainingSeats = ag["remaining_seats"].intOrNull(),
            actionStatus = a["by_action_status"].obj().ints("detected", "prevented", "failed"),
            actionCounts = a["by_action"].obj().ints("quarantine_file", "suspend_process", "kill_process", "registry_deletion"),
            byCategory = a["by_category"].obj().ints("file", "process", "powershell", "registry"),
            topAgents = nameCounts(r["top_agents"]),
        )
    }

    fun top(body: String): TopStats {
        val r = root(body)
        return TopStats(r["period"].str(), nameCounts(r["top_rules"]), nameCounts(r["top_agents"]), nameCounts(r["top_tactics"]),
            nameCounts(r["top_techniques"]), nameCounts(r["top_users"]), nameCounts(r["top_tags"]))
    }

    /** Items may be {name,count} or agent-style {hostname|agent_id, total_alerts, ip_address}. Duplicates (case-insensitive) are merged. */
    private fun nameCounts(e: JsonElement?): List<NameCount> =
        e.arr().mapNotNull { it as? JsonObject }.map { o ->
            val name = o["name"].str().ifBlank { o["hostname"].str() }.ifBlank { o["agent_id"].str() }.trim().ifEmpty { "Unknown" }
            val count = if (o["count"] != null) o["count"].int() else o["total_alerts"].int()
            NameCount(name, count, o["ip_address"].str())
        }.groupBy { it.name.lowercase() }
            .map { (_, v) -> v.first().copy(count = v.sumOf { it.count }) }
            .sortedByDescending { it.count }

    fun timeline(body: String): List<Bucket> = root(body)["buckets"].arr().mapNotNull { it as? JsonObject }.mapNotNull { o ->
        val ts = parseTs(o["timestamp"]) ?: return@mapNotNull null
        val s = o["by_severity"].obj()
        Bucket(ts, s["critical"].int(), s["high"].int(), s["medium"].int(), s["low"].int())
    }

    private fun parseTs(e: JsonElement?): Long? {
        val p = e as? JsonPrimitive ?: return null
        p.contentOrNull?.toDoubleOrNull()?.let { return if (it > 1e11) it.toLong() else (it * 1000).toLong() }
        return runCatching { Instant.parse(p.content).toEpochMilli() }.getOrNull()
    }

    fun hosts(body: String): HostsResult {
        val r = root(body)
        val list = r["hosts"].arr().mapNotNull { it as? JsonObject }.map { o ->
            val status = o["status"].str().lowercase()
            Host(
                id = o["agent_id"].str().ifEmpty { o["id"].str() },
                hostname = o["hostname"].str().ifEmpty { "—" },
                online = when (status) { "online" -> true; "offline" -> false; else -> null },
                lastSeenMs = o["last_seen_ms"].intOrNull()?.let { o["last_seen_ms"].str().replace(",", "").toDoubleOrNull()?.toLong() },
                user = o["user"].str().ifEmpty { o["user_name"].str() },
                type = o["type"].str().ifEmpty { o["device_type"].str() }.replaceFirstChar { it.uppercase() },
                os = o["os_version"].str().ifEmpty { o["os"].str() },
                ip = o["ip_address"].str(), agentVersion = o["agent_version"].str(),
                isolated = (o["is_isolated"] ?: o["isolated"]).str() == "true",
            )
        }
        return HostsResult(list, r["count"].intOrNull() ?: list.size, r["total_seats"].intOrNull(), r["remaining_seats"].intOrNull())
    }
}

/** Day totals (local time) for the stacked chart and a weekday×hour matrix for the heatmap. */
data class DayTotals(val date: LocalDate, val critical: Int, val high: Int, val medium: Int, val low: Int) {
    val total get() = critical + high + medium + low
}

fun List<Bucket>.byDay(zone: ZoneId = ZoneId.systemDefault()): List<DayTotals> =
    groupBy { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() }.toSortedMap().map { (d, bs) ->
        DayTotals(d, bs.sumOf { it.critical }, bs.sumOf { it.high }, bs.sumOf { it.medium }, bs.sumOf { it.low })
    }

/** [weekday 0=Mon..6=Sun][hour 0..23] -> alert count. */
fun List<Bucket>.heatmap(zone: ZoneId = ZoneId.systemDefault()): Array<IntArray> {
    val g = Array(7) { IntArray(24) }
    for (b in this) {
        val t = Instant.ofEpochMilli(b.epochMs).atZone(zone)
        g[t.dayOfWeek.value - 1][t.hour] += b.critical + b.high + b.medium + b.low
    }
    return g
}
