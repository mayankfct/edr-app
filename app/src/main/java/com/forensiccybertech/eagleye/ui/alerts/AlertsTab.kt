package com.forensiccybertech.eagleye.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forensiccybertech.eagleye.EagleEyeApp
import com.forensiccybertech.eagleye.data.Alert
import com.forensiccybertech.eagleye.data.AlertParser
import com.forensiccybertech.eagleye.data.Host
import com.forensiccybertech.eagleye.data.StatsParser
import com.forensiccybertech.eagleye.ui.components.MonoBlock
import com.forensiccybertech.eagleye.ui.components.PickerField
import com.forensiccybertech.eagleye.ui.components.SeverityChip
import com.forensiccybertech.eagleye.ui.components.SeverityOrder
import com.forensiccybertech.eagleye.ui.components.SuggestField
import com.forensiccybertech.eagleye.ui.components.TagChip
import com.forensiccybertech.eagleye.ui.components.eagleCardColors
import com.forensiccybertech.eagleye.ui.components.severityColor
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.MonoMedium
import com.forensiccybertech.eagleye.ui.theme.MonoSmall
import com.forensiccybertech.eagleye.util.ago
import com.forensiccybertech.eagleye.util.errorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20
private val CATEGORIES = listOf("process", "file", "device_connect", "powershell", "registry")

data class AlertsUi(
    val alerts: List<Alert> = emptyList(), val refreshing: Boolean = false, val loadingMore: Boolean = false,
    val endReached: Boolean = false, val error: String? = null,
    val severity: String? = null, val category: String? = null, val query: String = "",
    val agentId: String? = null, val user: String? = null,      // user filter is applied on the loaded alerts (API has no user filter)
    val hosts: List<Host> = emptyList(), val topUsers: List<String> = emptyList(),
)

class AlertsViewModel(private val app: EagleEyeApp) : ViewModel() {
    private val _ui = MutableStateFlow(AlertsUi(refreshing = true))
    val ui: StateFlow<AlertsUi> = _ui
    private var page = 0
    private var job: Job? = null

    init { refresh(); loadExtras() }

    /** Host list (for the scope dropdown) and top users (for suggestions); both best-effort. */
    private fun loadExtras() {
        viewModelScope.launch {
            runCatching { StatsParser.hosts(app.api.hosts.hosts().use { it.string() }).hosts }
                .getOrNull()?.let { _ui.value = _ui.value.copy(hosts = it.sortedBy { h -> h.hostname.lowercase() }) }
            runCatching { StatsParser.top(app.api.stats.top().use { it.string() }).users.map { u -> u.name } }
                .getOrNull()?.let { _ui.value = _ui.value.copy(topUsers = it) }
        }
    }

    fun setHost(id: String?) { _ui.value = _ui.value.copy(agentId = id); refresh() }
    fun setUser(u: String?) { _ui.value = _ui.value.copy(user = u) }
    fun setSeverity(v: String?) { _ui.value = _ui.value.copy(severity = v); refresh() }
    fun setCategory(v: String?) { _ui.value = _ui.value.copy(category = v); refresh() }
    fun search(q: String) { _ui.value = _ui.value.copy(query = q.trim()); refresh() }

    fun refresh() { page = 0; load(reset = true) }

    fun loadMore() {
        val s = _ui.value
        if (s.refreshing || s.loadingMore || s.endReached || s.alerts.isEmpty()) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            val f = _ui.value
            _ui.value = f.copy(refreshing = reset, loadingMore = !reset, error = null)
            try {
                val body = app.api.alerts.alerts(
                    page, PAGE_SIZE, severity = f.severity, processName = f.query.ifEmpty { null }, agentId = f.agentId, category = f.category
                ).use { it.string() }
                val batch = AlertParser.parseResponse(body)
                page++
                val cur = _ui.value
                _ui.value = cur.copy(
                    alerts = if (reset) batch else (cur.alerts + batch).distinctBy { it.id },
                    endReached = batch.size < PAGE_SIZE, refreshing = false, loadingMore = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = errorMessage(e), refreshing = false, loadingMore = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsTab(app: EagleEyeApp, onOpen: (Alert) -> Unit) {
    val vm: AlertsViewModel = viewModel(factory = viewModelFactory { initializer { AlertsViewModel(app) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.nearEnd() } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }
    val users = remember(ui.alerts, ui.topUsers) { (ui.topUsers + ui.alerts.map { it.user }).filter { it.isNotBlank() }.distinct() }
    val suggestions = remember(ui.alerts, users) { users + ui.alerts.map { it.processName }.filter { it.isNotBlank() }.distinct() }
    val shown = remember(ui.alerts, ui.user) { ui.alerts.filter { ui.user == null || it.user.equals(ui.user, ignoreCase = true) } }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), selectedLabelColor = MaterialTheme.colorScheme.primary,
    )

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestField(
                query, { query = it }, "Search by rule, process, user…", suggestions,
                onPick = { picked ->
                    if (picked in users) { vm.setUser(picked); query = "" }       // a user: filter the list by that user
                    else { query = picked; vm.search(picked) }                      // a process: server-side search
                },
                onSearch = { vm.search(query) }, modifier = Modifier.fillMaxWidth()
            )
            PickerField(
                "Scope", ui.hosts.firstOrNull { it.id == ui.agentId }?.hostname ?: "All Hosts (${ui.hosts.size})",
                listOf<Pair<String?, String>>(null to "All Hosts") + ui.hosts.map { it.id to it.hostname },
                onSelect = vm::setHost, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.user?.let { u -> FilterChip(true, { vm.setUser(null) }, { Text("User: $u  ✕") }, colors = chipColors) }
                FilterChip(ui.severity == null, { vm.setSeverity(null) }, { Text("All") }, colors = chipColors)
                SeverityOrder.forEach { s ->
                    FilterChip(ui.severity == s, { vm.setSeverity(if (ui.severity == s) null else s) },
                        { Text(s.replaceFirstChar { it.uppercase() }) }, colors = chipColors,
                        leadingIcon = { Box(Modifier.size(8.dp).background(severityColor(s), CircleShape)) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORIES.forEach { c ->
                    FilterChip(ui.category == c, { vm.setCategory(if (ui.category == c) null else c) }, { Text(c.replace('_', ' ')) }, colors = chipColors)
                }
            }
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("INVESTIGATION STREAM", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text("${shown.size} loaded", style = MonoSmall, color = Eagle.colors.glow)
                    }
                }
                ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (shown.isEmpty() && !ui.refreshing && ui.error == null) item { Text("No alerts match.") }
                items(shown, key = { it.id }) { AlertCard(it) { onOpen(it) } }
                if (ui.loadingMore) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Loading more telemetry…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun LazyListState.nearEnd(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
    return info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
}

@Composable
private fun AlertCard(a: Alert, onClick: () -> Unit) {
    val e = Eagle.colors
    val sev = e.severity(a.severity)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(8.dp), colors = eagleCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (a.severity == "critical") sev.border else e.border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityChip(a.severity)
                Text("· " + listOf(a.engine, a.category.replace('_', ' ')).filter { it.isNotEmpty() }.joinToString(" / "),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ago(a.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.ruleName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (a.tactics.isNotEmpty() || a.techniqueIds.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.tactics.firstOrNull()?.let { TagChip(it.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }, MaterialTheme.colorScheme.primary) }
                a.techniqueIds.firstOrNull()?.let { Text(it, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(
                Modifier.fillMaxWidth().background(e.mono, RoundedCornerShape(6.dp)).border(1.dp, e.border, RoundedCornerShape(6.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(a.hostname, style = MonoSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (a.processId.isNotEmpty()) Text("PID: ${a.processId}", style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val detail = a.commandLine.ifEmpty { a.path }.ifEmpty { a.processName }.ifEmpty { a.deviceName }
                if (detail.isNotEmpty()) Text(detail, style = MonoSmall, color = e.monoText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Incident investigation" page: summary, MITRE alignment, process ancestry timeline, forensic artifacts, identity context. */
@Composable
fun AlertDetail(a: Alert) {
    val e = Eagle.colors
    val sev = e.severity(a.severity)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityChip(a.severity)
                Text(listOf(a.engine, a.category.replace('_', ' ')).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (a.ruleId.isNotEmpty()) Text(a.ruleId, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(a.ruleName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("${a.timestamp}  (${ago(a.timestamp)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = eagleCardColors(), border = androidx.compose.foundation.BorderStroke(1.dp, e.border)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(a.hostname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (a.user.isNotEmpty()) Text(a.user, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (a.description.isNotEmpty() || a.autoAction.isNotEmpty()) DetailSection("Threat triage & summary") {
            if (a.description.isNotEmpty()) Text(a.description, style = MaterialTheme.typography.bodyMedium)
            if (a.autoAction.isNotEmpty()) Text("Automated mitigation · ${a.autoAction.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall,
                color = e.low.fg, modifier = Modifier.fillMaxWidth().background(e.low.bg, RoundedCornerShape(8.dp)).border(1.dp, e.low.border, RoundedCornerShape(8.dp)).padding(10.dp))
        }
        if (a.tactics.isNotEmpty() || a.techniqueIds.isNotEmpty() || a.tags.isNotEmpty()) DetailSection("MITRE ATT&CK alignment") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                a.tactics.forEach { TagChip(it.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }, MaterialTheme.colorScheme.secondary) }
            }
            a.techniqueIds.forEachIndexed { i, id ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TagChip(id, MaterialTheme.colorScheme.primary, mono = true)
                    a.techniqueNames.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (a.tags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                a.tags.forEach { TagChip(it, e.info.fg, mono = true) }
            }
        }
        if (a.processChain.isNotEmpty() || a.processName.isNotEmpty()) DetailSection("Execution ancestry tree") {
            // processChain is child -> parent; show oldest ancestor first, ending at the offending process.
            val nodes = a.processChain.reversed().map { Triple(it.name, it.pid, false) } + listOf(Triple(a.processName.ifEmpty { "process" }, a.processId, true))
            nodes.forEachIndexed { i, (name, pid, last) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(14.dp).background(if (last) sev.fg else e.info.fg, CircleShape))
                        if (i < nodes.lastIndex) Box(Modifier.size(width = 2.dp, height = 26.dp).background(e.border))
                    }
                    Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MonoMedium, fontWeight = FontWeight.SemiBold)
                            if (last) Text("OFFENDING", style = MonoSmall, color = sev.fg, modifier = Modifier.background(sev.bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                        if (pid.isNotEmpty()) Text("PID: $pid", style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        DetailSection("Forensic artifacts", trailing = "Tap string to copy") {
            com.forensiccybertech.eagleye.ui.components.LabeledBlock("Invoked command line", a.commandLine)
            com.forensiccybertech.eagleye.ui.components.LabeledBlock("Target path", a.path)
            com.forensiccybertech.eagleye.ui.components.LabeledBlock("Binary hash (SHA-256)", a.sha256)
            com.forensiccybertech.eagleye.ui.components.LabeledBlock("Device", listOf(a.deviceName, a.deviceSerial).filter { it.isNotEmpty() }.joinToString(" · "))
        }
        DetailSection("Endpoint & identity context") {
            ContextRow("User", a.user); ContextRow("Integrity level", a.integrity); ContextRow("Signer", a.signer)
            ContextRow("Agent", a.agentId); ContextRow("Alert ID", a.id)
        }
        Text("Mobile companion is in Read-Only Diagnostic Mode. Containment and host isolation are performed in the desktop console.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(e.chip, RoundedCornerShape(8.dp)).padding(12.dp))
    }
}

@Composable
private fun DetailSection(title: String, trailing: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    com.forensiccybertech.eagleye.ui.components.SectionCard(title, trailing = trailing?.let { t -> { Text(t, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, content = content)
}

@Composable
private fun ContextRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
    }
}
