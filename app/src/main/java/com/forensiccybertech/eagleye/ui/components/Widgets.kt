package com.forensiccybertech.eagleye.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forensiccybertech.eagleye.data.DayTotals
import com.forensiccybertech.eagleye.data.NameCount
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.MonoSmall
import java.text.NumberFormat

val SeverityOrder = listOf("critical", "high", "medium", "low")

/** Static severity foreground (for charts). Chips/badges use the theme-aware [SeverityChip]. */
fun severityColor(s: String) = when (s.lowercase()) {
    "critical" -> Color(0xFFEF4444)
    "high" -> Color(0xFFF97316)
    "medium" -> Color(0xFFF59E0B)
    "low" -> Color(0xFF10B981)
    else -> Color(0xFF94A3B8)
}

fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

private val CardShape = RoundedCornerShape(8.dp)

@Composable
fun eagleCardColors() = CardDefaults.cardColors(containerColor = Eagle.colors.card)

@Composable
fun SectionCard(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val e = Eagle.colors
    Card(modifier.fillMaxWidth(), shape = CardShape, colors = eagleCardColors(), border = BorderStroke(1.dp, e.border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(3.dp).height(18.dp).background(accent, RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Small mono pill used for section trailing text ("4-Hour Intervals", "92% Prevented"). */
@Composable
fun MonoPill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, style = MonoSmall, color = color, modifier = Modifier
        .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
fun KpiCard(
    label: String, value: String, accent: Color, modifier: Modifier = Modifier, footnote: String? = null, suffix: String? = null,
    badge: String? = null, badgeColor: Color? = null, container: Color? = null, borderColor: Color? = null, content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val e = Eagle.colors
    Card(
        modifier, shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container ?: e.card),
        border = BorderStroke(1.dp, borderColor ?: e.border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                suffix?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp)) }
            }
            badge?.let { b -> (badgeColor ?: accent).let { c -> Text(b, style = MonoSmall, color = c, modifier = Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) } }
            footnote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content?.invoke(this)
        }
    }
}

/** Pill severity chip: filled with the tier's dark container, tier-coloured text and border. */
@Composable
fun SeverityChip(severity: String, short: Boolean = false, modifier: Modifier = Modifier) {
    val c = Eagle.colors.severity(severity)
    val label = severity.uppercase().let { if (short) when (it) { "CRITICAL" -> "CRIT"; "MEDIUM" -> "MED"; else -> it } else it }
    Text(
        label, color = c.fg, style = MonoSmall.copy(fontWeight = FontWeight.Bold),
        modifier = modifier.background(c.bg, RoundedCornerShape(50)).border(1.dp, c.border, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable fun SeverityBadge(severity: String, modifier: Modifier = Modifier) = SeverityChip(severity, modifier = modifier)

@Composable
fun TagChip(text: String, color: Color, modifier: Modifier = Modifier, mono: Boolean = false) {
    Text(
        text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = if (mono) MonoSmall else MaterialTheme.typography.labelMedium,
        modifier = modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

fun copyToClipboard(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

/** Monospace data row with trailing copy icon (hashes, commands, paths). */
@Composable
fun MonoBlock(text: String, modifier: Modifier = Modifier, maxLines: Int = 3, copyable: Boolean = true, color: Color = Eagle.colors.monoText) {
    val ctx = LocalContext.current
    Row(
        modifier.fillMaxWidth().background(Eagle.colors.mono, RoundedCornerShape(6.dp))
            .then(if (copyable) Modifier.clickable { copyToClipboard(ctx, "value", text) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, Modifier.weight(1f), style = MonoSmall, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        if (copyable) Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LabeledBlock(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
        MonoBlock(value, maxLines = 6)
    }
}

data class Slice(val label: String, val value: Int, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, centerTop: String, centerBottom: String, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outline
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val stroke = 18.dp.toPx()
                val tl = Offset(stroke / 2, stroke / 2)
                val sz = Size(size.width - stroke, size.height - stroke)
                val total = slices.sumOf { it.value }
                if (total == 0) drawArc(ring, 0f, 360f, false, tl, sz, style = Stroke(stroke))
                else {
                    var start = -90f
                    for (s in slices) {
                        if (s.value == 0) continue
                        val sweep = 360f * s.value / total
                        drawArc(s.color, start, (sweep - 2f).coerceAtLeast(0.5f), false, tl, sz, style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTop, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(centerBottom.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEach {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(it.color, CircleShape))
                    Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(fmt(it.value), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun LegendRow(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (l, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).background(c, CircleShape)); Text(l, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Stacked bars (critical at the top of the stack, like the console) for the last days of alert volume. */
@Composable
fun DailyStackedBars(days: List<DayTotals>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) { Text("No data", style = MaterialTheme.typography.bodySmall); return }
    val max = days.maxOf { it.total }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.62f
            days.forEachIndexed { i, d ->
                var y = size.height
                val x = i * slot + (slot - barW) / 2
                for ((v, sev) in listOf(d.low to "low", d.medium to "medium", d.high to "high", d.critical to "critical")) {
                    if (v == 0) continue
                    val h = size.height * v / max
                    drawRoundRect(severityColor(sev), Offset(x, y - h), Size(barW, h), CornerRadius(2.dp.toPx()))
                    y -= h
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Text(it.date.dayOfWeek.name.take(1), Modifier.weight(1f), style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Weekday (rows, Mon..Sun) x 4-hour interval (columns) alert intensity, like the console's telemetry density matrix. */
@Composable
fun Heatmap(grid: Array<IntArray>, color: Color, modifier: Modifier = Modifier) {
    val cols = 6
    val agg = Array(7) { d -> IntArray(cols) { c -> (0 until 4).sumOf { grid[d][c * 4 + it] } } }
    val max = agg.maxOf { it.max() }.coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val days = "MTWTFSS"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(20.dp))
            repeat(cols) { Text("%02d".format(it * 4), Modifier.weight(1f), style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        for (d in 0 until 7) Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(days[d].toString(), Modifier.width(17.dp), style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (c in 0 until cols) {
                val v = agg[d][c]
                Box(Modifier.weight(1f).fillMaxHeight().background(if (v == 0) empty else color.copy(alpha = 0.2f + 0.8f * v / max), RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
    }
}

@Composable
fun BarList(items: List<NameCount>, color: Color, modifier: Modifier = Modifier, max: Int = 6) {
    val shown = items.take(max)
    if (shown.isEmpty()) { Text("No data", style = MaterialTheme.typography.bodySmall); return }
    val top = shown.maxOf { it.count }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(fmt(it.count), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
                ProgressLine(it.count.toFloat() / top, color)
            }
        }
    }
}

/** "#1  Rule name · sub   [412 hits]" row used by the leaderboards. */
@Composable
fun LeaderRow(rank: Int, name: String, sub: String, count: Int, unit: String = "hits", hot: Boolean = false) {
    val e = Eagle.colors
    Row(
        Modifier.fillMaxWidth().background(e.chip.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("#$rank", style = MonoSmall, color = if (rank == 1) e.critical.fg else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (sub.isNotBlank()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val c = if (hot || rank == 1) e.critical else e.info
        Text("${fmt(count)} $unit", style = MonoSmall.copy(fontWeight = FontWeight.Bold), color = c.fg,
            modifier = Modifier.background(c.bg, RoundedCornerShape(50)).border(1.dp, c.border, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Segmented control (tabs inside a card, e.g. Rules | Targets | ATT&CK). */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val e = Eagle.colors
    Row(Modifier.fillMaxWidth().background(e.chip, RoundedCornerShape(8.dp)).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Text(
                o, Modifier.weight(1f).background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
