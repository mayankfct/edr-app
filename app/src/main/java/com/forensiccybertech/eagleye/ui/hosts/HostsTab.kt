package com.forensiccybertech.eagleye.ui.hosts

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.forensiccybertech.eagleye.ui.components.ProgressLine
import com.forensiccybertech.eagleye.ui.components.SuggestField
import com.forensiccybertech.eagleye.ui.components.TagChip
import com.forensiccybertech.eagleye.ui.components.fmt
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.MonoSmall
import com.forensiccybertech.eagleye.util.ago
import com.forensiccybertech.eagleye.util.errorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
            .sortedWith(compareByDescending<Host> { it.isolated }.thenBy { it.online != true }.thenBy { it.hostname.lowercase() })   // isolated first, then offline
    }
    val e = Eagle.colors

    PullToRefreshBox(isRefreshing = ui.loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(16.dp)) {
            ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            ui.data?.let { d ->
                item {
                    val online = d.hosts.count { it.online == true }
                    val offline = d.hosts.count { it.online == false }
                    val unknown = d.hosts.size - online - offline
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiCard(
                            "Fleet devices", fmt(d.total), MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                            badge = d.remainingSeats?.let { "${fmt(it)} seats free" }, badgeColor = e.glow,
                            footnote = d.totalSeats?.let { "${fmt(it)} lic. pool" },
                        )
                        KpiCard("Online status", fmt(online), MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                            suffix = if (d.hosts.isNotEmpty()) "%.1f%%".format(100.0 * online / d.hosts.size) else null,
                            footnote = "${fmt(offline)} off · ${fmt(unknown)} unk") {
                            ProgressLine(if (d.hosts.isNotEmpty()) online.toFloat() / d.hosts.size else 0f, e.glow)
                        }
                    }
                }
            }
            item {
                val suggestions = remember(ui.data) {
                    ui.data?.hosts.orEmpty().flatMap { listOf(it.hostname, it.user, it.ip) }.filter { it.isNotBlank() && it != "—" }.distinct()
                }
                SuggestField(query, { query = it }, "Search host, user or IP", suggestions, onPick = { query = it }, onSearch = {}, modifier = Modifier.fillMaxWidth())
            }
            if (hosts.isEmpty() && !ui.loading && ui.error == null) item { Text("No devices.") }
            items(hosts, key = { it.id.ifEmpty { it.hostname } }) { HostCard(it) }
        }
    }
}

@Composable
private fun HostCard(h: Host) {
    val e = Eagle.colors
    val statusColor = when (h.online) { true -> e.glow; false -> e.info.fg; null -> e.info.fg }
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (h.isolated) e.critical.bg.copy(alpha = 0.55f) else e.card),
        border = BorderStroke(1.dp, if (h.isolated) e.critical.border else e.border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(if (h.isolated) e.critical.fg else statusColor, CircleShape))
                Text(h.hostname, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = if (h.isolated) e.critical.fg else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    h.isolated -> Text("[ISOLATED HOST]", style = MonoSmall.copy(fontWeight = FontWeight.Bold), color = Color.White,
                        modifier = Modifier.background(e.critical.fg.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
                    h.online == true -> TagChip("Online", e.glow)
                    h.online == false -> TagChip("Offline", e.info.fg)
                    else -> TagChip("Unknown", e.info.fg)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("System Profile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(h.os.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
                    if (h.type.isNotEmpty()) Text(h.type, style = MonoSmall, color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Primary Custodian", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(h.user.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                Modifier.fillMaxWidth().background(e.mono, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(h.ip.ifEmpty { "no ip" }, style = MonoSmall, color = if (h.isolated) e.critical.fg else MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(h.agentVersion.takeIf { it.isNotEmpty() }?.let { "Agent: v${it.removePrefix("v")}" }, h.lastSeenMs?.let { ago(it) }).joinToString("   "),
                    style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
