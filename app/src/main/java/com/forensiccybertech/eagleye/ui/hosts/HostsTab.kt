package com.forensiccybertech.eagleye.ui.hosts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forensiccybertech.eagleye.EagleEyeApp
import com.forensiccybertech.eagleye.data.Host
import com.forensiccybertech.eagleye.data.HostsResult
import com.forensiccybertech.eagleye.data.StatsParser
import com.forensiccybertech.eagleye.ui.components.KpiCard
import com.forensiccybertech.eagleye.ui.components.fmt
import com.forensiccybertech.eagleye.util.errorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class HostsUi(val data: HostsResult? = null, val loading: Boolean = true, val error: String? = null)

class HostsViewModel(private val app: EagleEyeApp) : ViewModel() {
    private val _ui = MutableStateFlow(HostsUi())
    val ui: StateFlow<HostsUi> = _ui

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val r = StatsParser.hosts(app.api.hosts.hosts().use { it.string() })
                _ui.value = HostsUi(r, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = errorMessage(e))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsTab(app: EagleEyeApp) {
    val vm: HostsViewModel = viewModel(factory = viewModelFactory { initializer { HostsViewModel(app) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val hosts = remember(ui.data, query) {
        ui.data?.hosts.orEmpty().filter { query.isBlank() || it.hostname.contains(query, true) || it.ip.contains(query) || it.user.contains(query, true) }
    }

    PullToRefreshBox(isRefreshing = ui.loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
            ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            ui.data?.let { d ->
                item {
                    val online = d.hosts.count { it.online == true }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiCard("Devices", fmt(d.total), MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                            footnote = d.totalSeats?.let { "${fmt(d.remainingSeats ?: 0)} of ${fmt(it)} seats free" })
                        KpiCard("Online", fmt(online), Color(0xFF43A047), Modifier.weight(1f), footnote = "${fmt(d.hosts.size - online)} offline/unknown")
                    }
                }
            }
            item {
                OutlinedTextField(query, { query = it }, label = { Text("Search host, IP or user") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (hosts.isEmpty() && !ui.loading && ui.error == null) item { Text("No devices.") }
            items(hosts, key = { it.id.ifEmpty { it.hostname } }) { HostRow(it) }
        }
    }
}

@Composable
private fun HostRow(h: Host) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(when (h.online) { true -> Color(0xFF43A047); false -> Color(0xFFF57C00); null -> Color.Gray }, CircleShape))
                Text(h.hostname, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (h.isolated) Text("ISOLATED", color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.background(Color(0xFFD32F2F), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(listOf(h.type, h.os).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text(listOf(h.ip, h.user).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            val extra = listOfNotNull(
                h.agentVersion.takeIf { it.isNotEmpty() }?.let { "Agent $it" },
                h.lastSeenMs?.let { "Last seen ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" },
            ).joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
