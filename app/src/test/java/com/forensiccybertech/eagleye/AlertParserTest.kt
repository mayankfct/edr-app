package com.forensiccybertech.eagleye

import com.forensiccybertech.eagleye.data.AlertParser
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertParserTest {
    // Mirrors the shape seen in the OpenSearch export: arrays and the process chain are JSON-in-strings.
    private val doc = """{"alert_id":"1795-1","severity":"HIGH","rule_name":"R","hostname":"H","engine":"sigma",
        "event_category":"process","mitre_tactic_names":"[\"execution\"]","mitre_technique_ids":"[\"T1204.002\"]",
        "process_chain":"[\"{\\n \\\"cmdline\\\": \\\"a.exe\\\", \\\"name\\\": \\\"a.exe\\\", \\\"pid\\\": 12944}\"]"}"""

    @Test fun parsesBareArray() {
        val a = AlertParser.parseResponse("[$doc]").single()
        assertEquals("high", a.severity)
        assertEquals(listOf("execution"), a.tactics)
        assertEquals("a.exe", a.processChain.single().name)
        assertEquals("12944", a.processChain.single().pid)
    }

    @Test fun parsesWrappedEnvelopes() {
        assertEquals(1, AlertParser.parseResponse("""{"data":{"items":[$doc]},"total":1}""").size)
        assertEquals(1, AlertParser.parseResponse("""{"hits":{"hits":[{"_source":$doc}]}}""").size)
        assertEquals(1, AlertParser.parseResponse("""{"alerts":[$doc]}""").size)
    }

    @Test fun skipsMalformedDocs() {
        assertEquals(1, AlertParser.parseResponse("""[{"foo":1},$doc]""").size)
    }
}
