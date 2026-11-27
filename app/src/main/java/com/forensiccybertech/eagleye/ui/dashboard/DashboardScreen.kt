package com.forensiccybertech.eagleye.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

private const val PAGE_SIZE = 20

data class AlertsUi(
    val alerts: List<Alert> = emptyList(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val severityFilter: String? = null,
)

class AlertsViewModel(private val app: EagleEyeApp) : ViewModel() {
    private val _ui = MutableStateFlow(AlertsUi(refreshing = true))
    val ui: StateFlow<AlertsUi> = _ui
    private var page = 0

    init { refresh() }

    fun setFilter(sev: String?) { _ui.value = _ui.value.copy(severityFilter = sev) }

    fun refresh() { page = 0; load(reset = true) }

    fun loadMore() {
        val s = _ui.value
        if (s.refreshing || s.loadingMore || s.endReached || s.alerts.isEmpty()) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(refreshing = reset, loadingMore = !reset, error = null)
            try {
                val body = app.api.alerts.alerts(page, PAGE_SIZE).use { it.string() }
                val batch = AlertParser.parseResponse(body)
                page++
                val merged = if (reset) batch else (_ui.value.alerts + batch).distinctBy { it.id }
                _ui.value = _ui.value.copy(alerts = merged, endReached = batch.size < PAGE_SIZE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _ui.value = _ui.value.copy(error = "Network error. Check your connection and pull to refresh.")
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "Couldn't load alerts.")
            } finally {
                _ui.value = _ui.value.copy(refreshing = false, loadingMore = false)
            }
        }
    }

    fun logout() { viewModelScope.launch { app.auth.logout() } }
}

fun severityColor(s: String) = when (s) {
    "critical" -> Color(0xFFB71C1C)
    "high" -> Color(0xFFE53935)
    "medium" -> Color(0xFFF9A825)
    "low" -> Color(0xFF1E88E5)
    else -> Color(0xFF757575)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(app: EagleEyeApp) {
    val vm: AlertsViewModel = viewModel(factory = viewModelFactory { initializer { AlertsViewModel(app) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Alert?>(null) }
    BackHandler(enabled = selected != null) { selected = null }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (selected == null) "Alerts" else "Alert detail") },
            actions = { TextButton(onClick = vm::logout) { Text("Sign out") } }
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val sel = selected
            if (sel != null) AlertDetail(sel) else AlertList(ui, vm, onOpen = { selected = it })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertList(ui: AlertsUi, vm: AlertsViewModel, onOpen: (Alert) -> Unit) {
    val shown = remember(ui.alerts, ui.severityFilter) {
        ui.alerts.filter { ui.severityFilter == null || it.severity == ui.severityFilter }
    }
    val counts = remember(ui.alerts) { ui.alerts.groupingBy { it.severity }.eachCount() }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedNearEnd(listState) }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (sev in listOf("critical", "high", "medium", "low")) {
                        val active = ui.severityFilter == sev
                        Column(
                            Modifier.weight(1f).clip(active, severityColor(sev))
                                .clickable { vm.setFilter(if (active) null else sev) }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${counts[sev] ?: 0}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                            Text(sev.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
            ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (shown.isEmpty() && !ui.refreshing && ui.error == null) item { Text("No alerts.") }
            items(shown, key = { it.id }) { AlertRow(it) { onOpen(it) } }
            if (ui.loadingMore) item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

private fun derivedNearEnd(state: androidx.compose.foundation.lazy.LazyListState) =
    androidx.compose.runtime.derivedStateOf {
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
    }

private fun Modifier.clip(active: Boolean, c: Color) =
    this.background(c.copy(alpha = if (active) 1f else 0.7f), RoundedCornerShape(8.dp))

@Composable
private fun AlertRow(a: Alert, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a.severity.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(severityColor(a.severity), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                Text(a.hostname, style = MaterialTheme.typography.labelLarge)
            }
            Text(a.ruleName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(listOf(a.timestamp, a.engine, a.category).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AlertDetail(a: Alert) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(a.ruleName, style = MaterialTheme.typography.titleMedium)
        Field("Severity", a.severity)
        Field("Time", a.timestamp)
        Field("Host", a.hostname)
        Field("User", a.user)
        Field("Engine / Rule", listOf(a.engine, a.ruleId).filter { it.isNotEmpty() }.joinToString(" / "))
        Field("Category", a.category)
        Field("Description", a.description)
        Field("Process", a.processName)
        Field("Command line", a.commandLine)
        Field("Path", a.path)
        Field("SHA-256", a.sha256)
        Field("MITRE tactics", a.tactics.joinToString())
        Field("MITRE techniques", a.techniqueIds.zip(a.techniqueNames + List(a.techniqueIds.size) { "" }).joinToString { (i, n) -> if (n.isEmpty()) i else "$i $n" })
        Field("Tags", a.tags.joinToString())
        Field("Automatic action", a.autoAction)
        Field("Device", listOf(a.deviceName, a.deviceSerial).filter { it.isNotEmpty() }.joinToString(" · "))
        Field("Alert ID", a.id)
        if (a.processChain.isNotEmpty()) {
            Text("Process chain (child → parent)", style = MaterialTheme.typography.labelLarge)
            a.processChain.forEach { Text("${it.name} (${it.pid})\n${it.cmdline}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
