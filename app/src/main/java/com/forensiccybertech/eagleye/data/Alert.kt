package com.forensiccybertech.eagleye.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ProcessNode(val name: String, val pid: String, val cmdline: String)

data class Alert(
    val id: String,
    val timestamp: String,
    val severity: String,          // critical | high | medium | low | unknown
    val ruleName: String,
    val ruleId: String,
    val engine: String,            // sigma | yara
    val category: String,          // process | file | device_connect ...
    val hostname: String,
    val agentId: String,
    val user: String,
    val processName: String,
    val commandLine: String,
    val path: String,
    val sha256: String,
    val tactics: List<String>,
    val techniqueIds: List<String>,
    val techniqueNames: List<String>,
    val tags: List<String>,
    val description: String,
    val autoAction: String,
    val deviceName: String,
    val deviceSerial: String,
    val processChain: List<ProcessNode>,
    val processId: String = "",
    val integrity: String = "",
    val signer: String = "",
)

/**
 * Tolerant parser. OpenSearch exports/APIs differ in shape: values may be real JSON arrays/numbers or
 * JSON-in-a-string ("[\"a\",\"b\"]", "1,234"). Both are accepted. Missing fields become "".
 */
object AlertParser {
    private val json = Json { isLenient = true }
    private val LIST_KEYS = listOf("alerts", "data", "items", "results", "hits", "records", "content")

    /** Extracts the alert documents from any of: [..], {alerts:[..]}, {data:{items:[..]}}, {hits:{hits:[{_source}]}}. */
    fun parseResponse(body: String): List<Alert> {
        val root = json.parseToJsonElement(body)
        return findDocs(root, 0).mapNotNull { runCatching { toAlert(it) }.getOrNull() }
    }

    private fun findDocs(e: JsonElement, depth: Int): List<JsonObject> {
        if (depth > 4) return emptyList()
        when (e) {
            is JsonArray -> return e.mapNotNull { (it as? JsonObject)?.let { o -> (o["_source"] as? JsonObject) ?: o } }
            is JsonObject -> for (k in LIST_KEYS) {
                val child = e[k] ?: continue
                val found = findDocs(child, depth + 1)
                if (found.isNotEmpty()) return found
            }
            else -> {}
        }
        return emptyList()
    }

    fun toAlert(o: JsonObject): Alert {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()
        val chain = o["process_chain"].asList().mapNotNull { raw ->
            val obj = when (raw) {
                is JsonObject -> raw
                is JsonPrimitive -> runCatching { json.parseToJsonElement(raw.content) as? JsonObject }.getOrNull()
                else -> null
            } ?: return@mapNotNull null
            fun f(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            ProcessNode(f("name"), f("pid"), f("cmdline"))
        }
        val id = s("alert_id").ifEmpty { s("_id") }
        return Alert(
            id = id,
            timestamp = s("@timestamp"),
            severity = s("severity").lowercase().ifEmpty { "unknown" },
            ruleName = s("rule_name"), ruleId = s("rule_id"),
            engine = s("engine"), category = s("event_category"),
            hostname = s("hostname"), agentId = s("agent_id"), user = s("user_name"),
            processName = s("process_name"), commandLine = s("command_line"),
            path = s("image_path").ifEmpty { s("path") }, sha256 = s("sha256"),
            tactics = o["mitre_tactic_names"].asList().strings(),
            techniqueIds = o["mitre_technique_ids"].asList().strings(),
            techniqueNames = o["mitre_technique_names"].asList().strings(),
            tags = o["detection_tags"].asList().strings(),
            description = s("engine_data.description"),
            autoAction = s("auto_action_name").takeIf { it != "unknown" }.orEmpty(),
            deviceName = s("device_name"), deviceSerial = s("serial_number"),
            processChain = chain,
            processId = s("process_id").replace(",", ""),
            integrity = s("integrity_level").takeIf { it != "undefined" }.orEmpty(),
            signer = s("image_signer").ifEmpty { s("signer") },
        ).takeIf { id.isNotEmpty() } ?: error("alert without id")
    }

    private fun JsonElement?.asList(): List<JsonElement> = when (this) {
        null, is JsonNull -> emptyList()
        is JsonArray -> this
        is JsonPrimitive -> if (isString && content.trimStart().startsWith("["))
            runCatching { (json.parseToJsonElement(content) as JsonArray).toList() }.getOrDefault(emptyList())
        else if (content.isNotEmpty()) listOf(this) else emptyList()
        else -> emptyList()
    }

    private fun List<JsonElement>.strings() = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
