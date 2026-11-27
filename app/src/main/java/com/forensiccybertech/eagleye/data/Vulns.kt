package com.forensiccybertech.eagleye.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class VulnSummary(
    val open: Int, val patched: Int, val suppressed: Int, val kev: Int, val hosts: Int, val mttpDays: String,
    val bySeverity: Map<String, Int>, val byPriority: Map<String, Int>,
)

data class TopCve(val cveId: String, val title: String, val cvss: Double, val epss: Double, val severity: String, val kev: Boolean, val hosts: Int)

data class Finding(
    val key: String, val cveId: String, val title: String, val component: String, val version: String,
    val cvss: Double, val severity: String, val priority: String, val status: String, val hostname: String, val kev: Boolean,
)

data class FindingsPage(val findings: List<Finding>, val total: Int)

/** Shapes taken from the console's frontend code (summary: total_open/by_severity/by_priority..., list: {findings,total}, top CVEs: {cves}). */
object VulnParser {
    private val json = Json { isLenient = true }
    private fun root(b: String) = json.parseToJsonElement(b) as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.obj() = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray) ?: emptyList()
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()
    private fun JsonElement?.int() = str().replace(",", "").toDoubleOrNull()?.toInt() ?: 0
    private fun JsonElement?.dbl() = str().toDoubleOrNull() ?: 0.0
    private fun JsonElement?.bool() = str().let { it == "true" || it == "1" }
    private fun JsonObject.counts() = entries.associate { it.key.uppercase() to it.value.int() }

    fun summary(body: String): VulnSummary {
        val r = root(body)
        return VulnSummary(
            open = r["total_open"].int(), patched = r["total_patched"].int(), suppressed = r["total_suppressed"].int(),
            kev = r["kev_count"].int(), hosts = r["affected_hosts"].int(), mttpDays = r["mttp_days"].str().ifEmpty { "—" },
            bySeverity = r["by_severity"].obj().counts(), byPriority = r["by_priority"].obj().counts(),
        )
    }

    fun topCves(body: String): List<TopCve> = root(body)["cves"].arr().mapNotNull { it as? JsonObject }.map {
        TopCve(it["cve_id"].str(), it["title"].str(), it["cvss_score"].dbl(), it["epss_score"].dbl(), it["cvss_severity"].str(), it["is_kev"].bool(), it["affected_hosts"].int())
    }

    fun findings(body: String): FindingsPage {
        val r = root(body)
        val list = r["findings"].arr().mapNotNull { it as? JsonObject }.mapIndexed { i, o ->
            Finding(
                key = listOf(o["id"].str(), o["cve_id"].str(), o["hostname"].str(), o["display_name"].str()).joinToString("|") + "#$i",
                cveId = o["cve_id"].str(), title = o["title"].str(), component = o["display_name"].str(),
                version = o["installed_version"].str(), cvss = o["cvss_score"].dbl(), severity = o["cvss_severity"].str(),
                priority = o["priority"].str(), status = o["status"].str(), hostname = o["hostname"].str(), kev = o["is_kev"].bool(),
            )
        }
        return FindingsPage(list, if (r["total"] != null) r["total"].int() else list.size)
    }
}
