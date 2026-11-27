package com.forensiccybertech.eagleye

import com.forensiccybertech.eagleye.data.StatsParser
import com.forensiccybertech.eagleye.data.byDay
import com.forensiccybertech.eagleye.data.heatmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Shapes inferred from the console's own frontend code; confirm against live responses.
class StatsParserTest {
    @Test fun summary() {
        val s = StatsParser.summary("""{"alerts":{"total":1234,"last_7d":{"change_pct":-4.5},"today":{"current":{"critical":2,"high":5}},
            "by_action_status":{"detected":10,"prevented":3},"by_category":{"file":7}},
            "agents":{"total":9,"online":6,"offline":3,"total_seats":10,"remaining_seats":1},
            "top_agents":[{"hostname":"H1","total_alerts":4,"ip_address":"10.0.0.1"}]}""")
        assertEquals(1234, s.totalAlerts); assertEquals(-4.5, s.changePct!!, 0.0)
        assertEquals(5, s.today["high"]); assertEquals(0, s.today["low"])
        assertEquals(6, s.online); assertEquals(1, s.remainingSeats)
        assertEquals("H1", s.topAgents.single().name); assertEquals(4, s.topAgents.single().count)
    }

    @Test fun topMergesDuplicateNamesCaseInsensitively() {
        val t = StatsParser.top("""{"period":"7d","top_rules":[{"name":"Rule A","count":2},{"name":"rule a","count":3},{"name":"B","count":1}]}""")
        assertEquals(2, t.rules.size); assertEquals(5, t.rules.first().count)
    }

    @Test fun timelineAndAggregations() {
        val b = StatsParser.timeline("""{"buckets":[{"timestamp":"2026-11-26T10:00:00Z","by_severity":{"critical":1,"high":2}},
            {"timestamp":1795700000000,"by_severity":{"low":4}},{"by_severity":{"low":9}}]}""")
        assertEquals(2, b.size)                         // bucket without a timestamp is dropped
        assertEquals(7, b.byDay().sumOf { it.total })
        assertEquals(7, b.heatmap().sumOf { it.sum() })
    }

    @Test fun hosts() {
        val h = StatsParser.hosts("""{"count":2,"total_seats":5,"remaining_seats":3,"hosts":[
            {"agent_id":"a1","hostname":"PC1","status":"online","type":"workstation","is_isolated":true,"last_seen_ms":1795700000000},
            {"id":"a2","hostname":"SRV","status":"offline"}]}""")
        assertEquals(2, h.total); assertEquals(true, h.hosts[0].online); assertEquals(false, h.hosts[1].online)
        assertEquals(true, h.hosts[0].isolated); assertEquals("Workstation", h.hosts[0].type)
        assertEquals(1795700000000L, h.hosts[0].lastSeenMs); assertNull(h.hosts[1].lastSeenMs)
    }
}
