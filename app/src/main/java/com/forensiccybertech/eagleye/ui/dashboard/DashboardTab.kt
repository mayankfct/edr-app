package com.forensiccybertech.eagleye.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forensiccybertech.eagleye.EagleEyeApp
import com.forensiccybertech.eagleye.data.Alert
import com.forensiccybertech.eagleye.data.AlertParser
import com.forensiccybertech.eagleye.data.Bucket
import com.forensiccybertech.eagleye.data.NameCount
import com.forensiccybertech.eagleye.data.StatsParser
import com.forensiccybertech.eagleye.data.Summary
import com.forensiccybertech.eagleye.data.TopStats
import com.forensiccybertech.eagleye.data.byDay
import com.forensiccybertech.eagleye.data.heatmap
import com.forensiccybertech.eagleye.ui.components.BarList
import com.forensiccybertech.eagleye.ui.components.DailyStackedBars
import com.forensiccybertech.eagleye.ui.components.DonutChart
import com.forensiccybertech.eagleye.ui.components.Heatmap
import com.forensiccybertech.eagleye.ui.components.KpiCard
import com.forensiccybertech.eagleye.ui.components.LeaderRow
import com.forensiccybertech.eagleye.ui.components.LegendRow
import com.forensiccybertech.eagleye.ui.components.MonoPill
import com.forensiccybertech.eagleye.ui.components.ProgressLine
import com.forensiccybertech.eagleye.ui.components.SectionCard
import com.forensiccybertech.eagleye.ui.components.SegmentedTabs
import com.forensiccybertech.eagleye.ui.components.SeverityChip
import com.forensiccybertech.eagleye.ui.components.SeverityOrder
import com.forensiccybertech.eagleye.ui.components.Slice
import com.forensiccybertech.eagleye.ui.components.eagleCardColors
import com.forensiccybertech.eagleye.ui.components.fmt
import com.forensiccybertech.eagleye.ui.components.severityColor
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.MonoSmall
import com.forensiccybertech.eagleye.util.clock
import com.forensiccybertech.eagleye.util.errorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashUi(
    val summary: Summary? = null, val top: TopStats? = null, val buckets: List<Bucket> = emptyList(),
    val recent: List<Alert> = emptyList(), val loading: Boolean = true, val error: String? = null,
)

class DashboardViewModel(private val app: EagleEyeApp) : ViewModel() {
    private val _ui = MutableStateFlow(DashUi())
    val ui: StateFlow<DashUi> = _ui

    init { refresh() }

    /** Each widget loads independently: one failing endpoint doesn't blank the whole dashboard. */
    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val errors = mutableListOf<Throwable>()
            suspend fun <T> safe(block: suspend () -> T): T? = try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += e; null }
            coroutineScope {
                val s = async { safe { StatsParser.summary(app.api.stats.summary().use { it.string() }) } }
                val t = async { safe { StatsParser.top(app.api.stats.top().use { it.string() }) } }
                val tl = async { safe { StatsParser.timeline(app.api.stats.timeline().use { it.string() }) } }
                val r = async { safe { AlertParser.parseResponse(app.api.alerts.alerts(0, 5).use { it.string() }) } }
                val sv = s.await(); val tv = t.await(); val tlv = tl.await(); val rv = r.await()
                val cur = _ui.value
                _ui.value = cur.copy(summary = sv ?: cur.summary, top = tv ?: cur.top, buckets = tlv ?: cur.buckets, recent = rv ?: cur.recent)
            }
            _ui.value = _ui.value.copy(loading = false, error = if (errors.size >= 4) errorMessage(errors.first()) else null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTab(app: EagleEyeApp, onOpenAlert: (Alert) -> Unit, onSeeAllAlerts: () -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = viewModelFactory { initializer { DashboardViewModel(app) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val days = remember(ui.buckets) { ui.buckets.byDay() }
    val heat = remember(ui.buckets) { ui.buckets.heatmap() }
    var board by rememberSaveable { mutableIntStateOf(0) }
    val e = Eagle.colors

    PullToRefreshBox(isRefreshing = ui.loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val s = ui.summary
            if (s != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val change = s.changePct
                    KpiCard(
                        "Total alerts", fmt(s.totalAlerts), MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                        badge = change?.let { "${if (it >= 0) "▲" else "▼"} ${"%.1f".format(kotlin.math.abs(it))}% 7d" },
                        badgeColor = if ((change ?: 0.0) > 0) e.critical.fg else e.low.fg,
                        footnote = "${fmt((s.today["critical"] ?: 0) + (s.today["high"] ?: 0))} critical/high today",
                    )
                    val pct = if (s.agentsTotal > 0) 100f * s.online / s.agentsTotal else 0f
                    KpiCard(
                        "Fleet online", fmt(s.online), MaterialTheme.colorScheme.primary, Modifier.weight(1f), suffix = "/${fmt(s.agentsTotal)}",
                        badge = "%.0f%%".format(pct), badgeColor = e.glow,
                        footnote = "${fmt(s.offline)} offline",
                    )
                }
                SectionCard("Today by severity", trailing = { MonoPill("TODAY") }) {
                    DonutChart(
                        SeverityOrder.map { Slice(it.replaceFirstChar { c -> c.uppercase() }, s.today[it] ?: 0, severityColor(it)) },
                        fmt(s.today.values.sum()), "triaged"
                    )
                }
            }
            SectionCard("Alert velocity (7 days)", trailing = {
                LegendRow(listOf("Crit" to severityColor("critical"), "High" to severityColor("high"), "Med" to severityColor("medium")))
            }) { DailyStackedBars(days) }
            SectionCard("Telemetry density matrix", trailing = { MonoPill("4-HOUR INTERVALS") }) { Heatmap(heat, MaterialTheme.colorScheme.tertiary) }

            if (s != null) {
                val total = (s.actionStatus.values.sum()).coerceAtLeast(1)
                val prevented = s.actionStatus["prevented"] ?: 0
                SectionCard("Auto-mitigation guard", trailing = { MonoPill("${100 * prevented / total}% PREVENTED", e.low.fg) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("prevented" to e.low.fg, "detected" to MaterialTheme.colorScheme.primary, "failed" to e.critical.fg).forEach { (k, c) ->
                            Column(Modifier.weight(1f).background(e.chip, RoundedCornerShape(8.dp)).padding(10.dp)) {
                                Text(k.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fmt(s.actionStatus[k] ?: 0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c)
                            }
                        }
                    }
                    val labels = mapOf("quarantine_file" to "Host Isolation (Quarantine)", "suspend_process" to "Process Suspension", "kill_process" to "Process Terminate (SIGKILL)", "registry_deletion" to "Registry Reversion")
                    val actionTotal = s.actionCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    s.actionCounts.forEach { (k, v) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(labels[k] ?: k, style = MaterialTheme.typography.bodySmall); Text(fmt(v), style = MonoSmall)
                            }
                            ProgressLine(v.toFloat() / actionTotal, MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            ui.top?.let { t ->
                SectionCard("SOC leaderboards", accent = e.critical.fg) {
                    SegmentedTabs(listOf("Rules", "Targets", "ATT&CK"), board) { board = it }
                    val rows: List<NameCount> = when (board) {
                        0 -> t.rules
                        1 -> t.agents.ifEmpty { ui.summary?.topAgents.orEmpty() }
                        else -> t.techniques.ifEmpty { t.tactics }
                    }
                    if (rows.isEmpty()) Text("No data", style = MaterialTheme.typography.bodySmall)
                    rows.take(5).forEachIndexed { i, r -> LeaderRow(i + 1, r.name, r.sub, r.count, unit = if (board == 1) "alerts" else "hits") }
                }
                SectionCard("MITRE tactics", accent = MaterialTheme.colorScheme.secondary) { BarList(t.tactics, MaterialTheme.colorScheme.secondary) }
                SectionCard("Top users") { BarList(t.users, MaterialTheme.colorScheme.tertiary) }
                SectionCard("Top tags") { BarList(t.tags, e.warning) }
            }
            ui.summary?.let { s ->
                SectionCard("Alerts by category") { BarList(s.byCategory.map { NameCount(it.key.replaceFirstChar { c -> c.uppercase() }, it.value) }, e.low.fg) }
            }

            SectionCard("Recent stream ingestion", accent = e.critical.fg, trailing = { MonoPill("STREAM", e.glow) }) {
                if (ui.recent.isEmpty()) Text("No recent alerts", style = MaterialTheme.typography.bodySmall)
                ui.recent.forEach { a -> StreamRow(a) { onOpenAlert(a) } }
                Text("See all alerts →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSeeAllAlerts).padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun StreamRow(a: Alert, onClick: () -> Unit) {
    val sev = Eagle.colors.severity(a.severity)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(8.dp), colors = eagleCardColors()) {
        Row(Modifier.border(1.dp, sev.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityChip(a.severity, short = true)
                    Text(clock(a.timestamp), style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(a.hostname, style = MonoSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(a.ruleName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                val meta = listOf(a.processName, if (a.processId.isNotEmpty()) "PID: ${a.processId}" else "").filter { it.isNotEmpty() }.joinToString("  ")
                if (meta.isNotEmpty()) Text(meta, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
