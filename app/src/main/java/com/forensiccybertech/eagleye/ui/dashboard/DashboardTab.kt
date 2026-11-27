package com.forensiccybertech.eagleye.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.forensiccybertech.eagleye.ui.components.SectionCard
import com.forensiccybertech.eagleye.ui.components.SeverityBadge
import com.forensiccybertech.eagleye.ui.components.SeverityOrder
import com.forensiccybertech.eagleye.ui.components.Slice
import com.forensiccybertech.eagleye.ui.components.fmt
import com.forensiccybertech.eagleye.ui.components.severityColor
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
                _ui.value = cur.copy(
                    summary = sv ?: cur.summary, top = tv ?: cur.top,
                    buckets = tlv ?: cur.buckets, recent = rv ?: cur.recent,
                )
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

    PullToRefreshBox(isRefreshing = ui.loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val s = ui.summary
            if (s != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Total alerts", fmt(s.totalAlerts), MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                        footnote = s.changePct?.let { "${if (it >= 0) "▲" else "▼"} ${"%.1f".format(kotlin.math.abs(it))}% vs prev 7d" })
                    KpiCard("Endpoints online", "${fmt(s.online)}/${fmt(s.agentsTotal)}", Color(0xFF43A047), Modifier.weight(1f),
                        footnote = if (s.offline > 0) "${fmt(s.offline)} offline" else "All reporting")
                }
                SectionCard("Today by severity") {
                    DonutChart(
                        SeverityOrder.map { Slice(it.replaceFirstChar { c -> c.uppercase() }, s.today[it] ?: 0, severityColor(it)) },
                        fmt(s.today.values.sum()), "today"
                    )
                }
            }
            SectionCard("Alert volume", "Last 7 days · critical → low") { DailyStackedBars(days) }
            SectionCard("Activity heatmap", "Hourly pattern") { Heatmap(heat, MaterialTheme.colorScheme.primary) }

            if (s != null) {
                SectionCard("Automated response") {
                    BarList(s.actionStatus.map { com.forensiccybertech.eagleye.data.NameCount(it.key.replaceFirstChar { c -> c.uppercase() }, it.value) },
                        Color(0xFF1E88E5))
                    Text("Actions taken", style = MaterialTheme.typography.labelLarge)
                    BarList(s.actionCounts.map { com.forensiccybertech.eagleye.data.NameCount(it.key.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }, it.value) },
                        Color(0xFF8E24AA))
                }
                SectionCard("Alerts by category") {
                    BarList(s.byCategory.map { com.forensiccybertech.eagleye.data.NameCount(it.key.replaceFirstChar { c -> c.uppercase() }, it.value) },
                        Color(0xFF00897B))
                }
            }
            ui.top?.let { t ->
                SectionCard("Top detection rules", t.period.takeIf { it.isNotEmpty() }) { BarList(t.rules, Color(0xFFE53935)) }
                SectionCard("Top targeted devices") { BarList(t.agents.ifEmpty { ui.summary?.topAgents.orEmpty() }, Color(0xFFF57C00)) }
                SectionCard("MITRE tactics") { BarList(t.tactics, Color(0xFF5E35B1)) }
                SectionCard("MITRE techniques") { BarList(t.techniques, Color(0xFF3949AB)) }
                SectionCard("Top users") { BarList(t.users, Color(0xFF00ACC1)) }
                SectionCard("Top tags") { BarList(t.tags, Color(0xFFD81B60)) }
            }
            SectionCard("Recent alerts") {
                if (ui.recent.isEmpty()) Text("No recent alerts", style = MaterialTheme.typography.bodySmall)
                ui.recent.forEach { a ->
                    Column(Modifier.fillMaxWidth().clickable { onOpenAlert(a) }.padding(vertical = 4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SeverityBadge(a.severity); Text(a.hostname, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(a.ruleName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("See all alerts →", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onSeeAllAlerts))
            }
        }
    }
}
