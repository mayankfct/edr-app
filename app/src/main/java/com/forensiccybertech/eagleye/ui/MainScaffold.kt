package com.forensiccybertech.eagleye.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.forensiccybertech.eagleye.EagleEyeApp
import com.forensiccybertech.eagleye.data.Alert
import com.forensiccybertech.eagleye.ui.alerts.AlertDetail
import com.forensiccybertech.eagleye.ui.alerts.AlertsTab
import com.forensiccybertech.eagleye.ui.dashboard.DashboardTab
import com.forensiccybertech.eagleye.ui.hosts.HostsTab
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Alerts("Alerts", Icons.Filled.NotificationsActive),
    Devices("Devices", Icons.Filled.Computer),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(app: EagleEyeApp) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by androidx.compose.runtime.remember { mutableStateOf<Alert?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = selected != null) { selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected != null) "Alert detail" else Tab.entries[tab].label) },
                navigationIcon = {
                    if (selected != null) IconButton({ selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { TextButton({ scope.launch { app.auth.logout() } }) { Text("Sign out") } }
            )
        },
        bottomBar = {
            if (selected == null) NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(tab == i, { tab = i }, { Icon(t.icon, t.label) }, label = { Text(t.label) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val sel = selected
            when {
                sel != null -> AlertDetail(sel)
                tab == 0 -> DashboardTab(app, onOpenAlert = { selected = it }, onSeeAllAlerts = { tab = 1 })
                tab == 1 -> AlertsTab(app, onOpen = { selected = it })
                else -> HostsTab(app)
            }
        }
    }
}
