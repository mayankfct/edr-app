package com.forensiccybertech.eagleye.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
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
import com.forensiccybertech.eagleye.ui.components.SeverityBadge
import com.forensiccybertech.eagleye.ui.components.SeverityOrder
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
)

class AlertsViewModel(private val app: EagleEyeApp) : ViewModel() {
    private val _ui = MutableStateFlow(AlertsUi(refreshing = true))
    val ui: StateFlow<AlertsUi> = _ui
    private var page = 0
    private var job: Job? = null

    init { refresh() }

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
                    page, PAGE_SIZE, severity = f.severity, processName = f.query.ifEmpty { null }, category = f.category
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

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it }, label = { Text("Search by process name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(ui.severity == null, { vm.setSeverity(null) }, { Text("All") })
            SeverityOrder.forEach { s -> FilterChip(ui.severity == s, { vm.setSeverity(if (ui.severity == s) null else s) }, { Text(s.replaceFirstChar { it.uppercase() }) }) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CATEGORIES.forEach { c -> FilterChip(ui.category == c, { vm.setCategory(if (ui.category == c) null else c) }, { Text(c.replace('_', ' ')) }) }
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
                ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (ui.alerts.isEmpty() && !ui.refreshing && ui.error == null) item { Text("No alerts match.") }
                items(ui.alerts, key = { it.id }) { AlertRow(it) { onOpen(it) } }
                if (ui.loadingMore) item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } }
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
private fun AlertRow(a: Alert, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(a.severity)
                Text(a.hostname, style = MaterialTheme.typography.labelLarge)
            }
            Text(a.ruleName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(listOf(a.timestamp, a.engine, a.category).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AlertDetail(a: Alert) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityBadge(a.severity); Text(a.hostname, style = MaterialTheme.typography.labelLarge)
        }
        Text(a.ruleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Field("Time", a.timestamp); Field("User", a.user)
        Field("Engine / Rule", listOf(a.engine, a.ruleId).filter { it.isNotEmpty() }.joinToString(" / "))
        Field("Category", a.category); Field("Description", a.description)
        Field("Process", a.processName); Field("Command line", a.commandLine); Field("Path", a.path); Field("SHA-256", a.sha256)
        Field("MITRE tactics", a.tactics.joinToString())
        Field("MITRE techniques", a.techniqueIds.mapIndexed { i, id -> a.techniqueNames.getOrNull(i)?.let { "$id $it" } ?: id }.joinToString())
        Field("Tags", a.tags.joinToString()); Field("Automatic action", a.autoAction)
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
