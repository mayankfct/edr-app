package com.forensiccybertech.eagleye.ui.vulns

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.forensiccybertech.eagleye.data.Finding
import com.forensiccybertech.eagleye.data.TopCve
import com.forensiccybertech.eagleye.data.VulnParser
import com.forensiccybertech.eagleye.data.VulnSummary
import com.forensiccybertech.eagleye.ui.components.DonutChart
import com.forensiccybertech.eagleye.ui.components.KpiCard
import com.forensiccybertech.eagleye.ui.components.SectionCard
import com.forensiccybertech.eagleye.ui.components.Slice
import com.forensiccybertech.eagleye.ui.components.TagChip
import com.forensiccybertech.eagleye.ui.components.eagleCardColors
import com.forensiccybertech.eagleye.ui.components.fmt
import com.forensiccybertech.eagleye.ui.components.severityColor
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.MonoSmall
import com.forensiccybertech.eagleye.util.errorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PAGE = 25
private val SEVERITIES = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW")

data class VulnsUi(
    val summary: VulnSummary? = null, val topCves: List<TopCve> = emptyList(),
    val findings: List<Finding> = emptyList(), val total: Int = 0, val severity: String? = null,
    val refreshing: Boolean = true, val loadingMore: Boolean = false, val error: String? = null,
)

class VulnsViewModel(private val app: EagleEyeApp) : ViewModel() {
    private val _ui = MutableStateFlow(VulnsUi())
    val ui: StateFlow<VulnsUi> = _ui
    private var offset = 0
    private var job: Job? = null

    init { refresh() }

    fun setSeverity(s: String?) { _ui.value = _ui.value.copy(severity = s); reloadFindings() }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(refreshing = true, error = null)
            try {
                // Summary + top CVEs are best-effort; the findings list decides whether the screen shows an error.
                runCatching { VulnParser.summary(app.api.vulns.summary().use { it.string() }) }.getOrNull()
                    ?.let { _ui.value = _ui.value.copy(summary = it) }
                runCatching { VulnParser.topCves(app.api.vulns.topCves().use { it.string() }) }.getOrNull()
                    ?.let { _ui.value = _ui.value.copy(topCves = it) }
                fetchFindings(reset = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = errorMessage(e))
            } finally {
                _ui.value = _ui.value.copy(refreshing = false)
            }
        }
    }

    private fun reloadFindings() {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(refreshing = true, error = null)
            try { fetchFindings(reset = true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.value = _ui.value.copy(error = errorMessage(e)) }
            finally { _ui.value = _ui.value.copy(refreshing = false) }
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.refreshing || s.loadingMore || s.findings.size >= s.total) return
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(loadingMore = true)
            try { fetchFindings(reset = false) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.value = _ui.value.copy(error = errorMessage(e)) }
            finally { _ui.value = _ui.value.copy(loadingMore = false) }
        }
    }

    private suspend fun fetchFindings(reset: Boolean) {
        if (reset) offset = 0
        val page = VulnParser.findings(app.api.vulns.findings(PAGE, offset, severity = _ui.value.severity).use { it.string() })
        offset += PAGE
        val cur = _ui.value
        _ui.value = cur.copy(findings = if (reset) page.findings else (cur.findings + page.findings).distinctBy { it.key }, total = page.total)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VulnsTab(app: EagleEyeApp) {
    val vm: VulnsViewModel = viewModel(factory = viewModelFactory { initializer { VulnsViewModel(app) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var kevOnly by remember { mutableStateOf(false) }
    val e = Eagle.colors
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf {
        val i = listState.layoutInfo
        i.totalItemsCount > 0 && (i.visibleItemsInfo.lastOrNull()?.index ?: 0) >= i.totalItemsCount - 4
    } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }
    val shown = remember(ui.findings, kevOnly) { if (kevOnly) ui.findings.filter { it.kev } else ui.findings }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), selectedLabelColor = MaterialTheme.colorScheme.primary,
    )

    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(16.dp)) {
            ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            ui.summary?.let { s ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KpiCard("Open CVEs", fmt(s.open), e.critical.fg, Modifier.weight(1f), footnote = "MTTP: ${s.mttpDays}d")
                            KpiCard("Patched", fmt(s.patched), e.low.fg, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KpiCard("Known exploited", "${fmt(s.kev)} Active", e.critical.fg, Modifier.weight(1f),
                                footnote = "Active in wild", container = e.critical.bg, borderColor = e.critical.border)
                            KpiCard("Affected hosts", fmt(s.hosts), MaterialTheme.colorScheme.primary, Modifier.weight(1f), badge = "${fmt(s.suppressed)} suppressed", badgeColor = e.info.fg)
                        }
                    }
                }
                item {
                    SectionCard("Posture telemetry breakdown", subtitle = "Findings by severity") {
                        DonutChart(
                            listOf("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO").map {
                                Slice(it.lowercase().replaceFirstChar { c -> c.uppercase() }, s.bySeverity[it] ?: 0, severityColor(it))
                            },
                            fmt(s.bySeverity.values.sum()), "total"
                        )
                        val pTotal = s.byPriority.values.sum().coerceAtLeast(1)
                        if (s.byPriority.isNotEmpty()) {
                            Text("Priority triage distribution", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().height(8.dp).background(e.chip, RoundedCornerShape(4.dp))) {
                                SEVERITIES.forEach { p -> val v = s.byPriority[p] ?: 0
                                    if (v > 0) Box(Modifier.weight(v.toFloat() / pTotal).fillMaxSize().background(severityColor(p))) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SEVERITIES.forEach { p -> s.byPriority[p]?.let { Text("${p.lowercase().replaceFirstChar { c -> c.uppercase() }} (${fmt(it)})", style = MonoSmall, color = severityColor(p)) } }
                            }
                        }
                    }
                }
            }
            val kev = ui.topCves.filter { it.kev }.ifEmpty { ui.topCves }
            if (kev.isNotEmpty()) item {
                SectionCard("Active exploited (KEV) intel", accent = e.critical.fg) { kev.take(5).forEach { CveRow(it) } }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FINDINGS (${fmt(ui.total)})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(ui.severity == null && !kevOnly, { vm.setSeverity(null); kevOnly = false }, { Text("All (${fmt(ui.total)})") }, colors = chipColors)
                        FilterChip(kevOnly, { kevOnly = !kevOnly }, { Text("KEV only") }, colors = chipColors)
                        SEVERITIES.forEach { s -> FilterChip(ui.severity == s, { vm.setSeverity(if (ui.severity == s) null else s) }, { Text(s.lowercase().replaceFirstChar { it.uppercase() }) }, colors = chipColors) }
                    }
                }
            }
            if (shown.isEmpty() && !ui.refreshing && ui.error == null) item { Text("No findings.") }
            items(shown, key = { it.key }) { FindingCard(it) }
            if (ui.loadingMore) item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun CvssChip(score: Double, severity: String) {
    val c = Eagle.colors.severity(severity)
    Text("CVSS %.1f".format(score), color = c.fg, style = MonoSmall.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.background(c.bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun KevChip() {
    val c = Eagle.colors.critical
    Text("⚠ CISA KEV", color = c.fg, style = MonoSmall.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.background(c.bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun CveRow(c: TopCve) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val sc = Eagle.colors.severity(c.severity)
        Text("%.1f".format(c.cvss), color = sc.fg, style = MonoSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.background(sc.bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(c.cveId, style = MonoSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${fmt(c.hosts)} hosts", style = MonoSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FindingCard(f: Finding) {
    val e = Eagle.colors
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = eagleCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (f.kev) e.critical.border else e.border)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CvssChip(f.cvss, f.severity)
                if (f.kev) KevChip()
                if (f.priority.isNotEmpty()) TagChip(f.priority, MaterialTheme.colorScheme.secondary, mono = true)
                Box(Modifier.weight(1f))
                if (f.status.isNotEmpty()) Text(f.status.replace('_', ' '), style = MonoSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(f.cveId, style = MonoSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            if (f.title.isNotEmpty()) Text(f.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (f.component.isNotEmpty()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Affected package:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(listOf(f.component, f.version).filter { it.isNotEmpty() }.joinToString(" "), style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
            if (f.hostname.isNotEmpty()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Affected host:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(f.hostname, style = MonoSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
