package com.forensiccybertech.eagleye.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forensiccybertech.eagleye.R
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.MonoSmall
import com.forensiccybertech.eagleye.util.onlineFlow

/** Connectivity pill: "LIVE" while the device is online, "OFFLINE" otherwise. */
@Composable
fun LivePill() {
    val ctx = LocalContext.current
    val online by remember { ctx.applicationContext.onlineFlow() }.collectAsStateWithLifecycle(initialValue = true)
    val c = if (online) Eagle.colors.glow else Eagle.colors.info.fg
    Row(
        Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).background(c, CircleShape))
        Text(if (online) "LIVE" else "OFFLINE", style = MonoSmall, color = c, fontWeight = FontWeight.Bold)
    }
}

/** Top bar: logo, "EagleEye [VANGUARD]" and the screen name; back arrow instead of the logo on detail screens. */
@Composable
fun AppHeader(subtitle: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) IconButton(onBack, Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            else Image(painterResource(R.drawable.eagleye_logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(Eagle.colors.canvas).border(1.dp, Eagle.colors.border, RoundedCornerShape(10.dp)).padding(4.dp), contentScale = ContentScale.Fit)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("EagleEye", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("VANGUARD", style = MonoSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LivePill()
            actions()
        }
        HorizontalDivider(color = Eagle.colors.border)
    }
}

@Composable
fun OverflowIcon(onClick: () -> Unit) = IconButton(onClick, Modifier.size(40.dp)) { Icon(Icons.Filled.MoreVert, "Menu") }
