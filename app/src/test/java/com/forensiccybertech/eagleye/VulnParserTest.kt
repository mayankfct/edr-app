package com.forensiccybertech.eagleye

import com.forensiccybertech.eagleye.data.VulnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Shapes inferred from the console's frontend code; confirm against live responses.
class VulnParserTest {
    @Test fun summary() {
        val s = VulnParser.summary("""{"total_open":12,"total_patched":3,"total_suppressed":1,"kev_count":2,"affected_hosts":5,"mttp_days":4.5,
            "by_severity":{"critical":2,"HIGH":4},"by_priority":{"CRITICAL":1}}""")
        assertEquals(12, s.open); assertEquals(2, s.kev); assertEquals("4.5", s.mttpDays)
        assertEquals(2, s.bySeverity["CRITICAL"]); assertEquals(4, s.bySeverity["HIGH"])   // keys normalised to upper case
    }

    @Test fun findingsAndTopCves() {
        val p = VulnParser.findings("""{"total":40,"findings":[{"cve_id":"CVE-2024-1","title":"T","cvss_score":9.8,"cvss_severity":"CRITICAL","is_kev":true,"hostname":"H"},
            {"cve_id":"CVE-2024-1","hostname":"H2"}]}""")
        assertEquals(40, p.total); assertEquals(2, p.findings.size)
        assertEquals(2, p.findings.map { it.key }.distinct().size); assertTrue(p.findings[0].kev)
        assertEquals("CVE-2024-2", VulnParser.topCves("""{"cves":[{"cve_id":"CVE-2024-2","cvss_score":"7.5","affected_hosts":3}]}""").single().cveId)
    }
}
