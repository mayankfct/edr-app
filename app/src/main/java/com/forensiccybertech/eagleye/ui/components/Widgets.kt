package com.forensiccybertech.eagleye.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forensiccybertech.eagleye.data.DayTotals
import com.forensiccybertech.eagleye.data.NameCount
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

val SeverityOrder = listOf("critical", "high", "medium", "low")

fun severityColor(s: String) = when (s) {
    "critical" -> Color(0xFFD32F2F)
    "high" -> Color(0xFFF57C00)
    "medium" -> Color(0xFFFBC02D)
    "low" -> Color(0xFF43A047)
    else -> Color(0xFF757575)
}

fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
fun KpiCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier, footnote: String? = null) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(12.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                footnote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

data class Slice(val label: String, val value: Int, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, centerTop: String, centerBottom: String, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val stroke = 20.dp.toPx()
                val tl = Offset(stroke / 2, stroke / 2)
                val sz = Size(size.width - stroke, size.height - stroke)
                val total = slices.sumOf { it.value }
                if (total == 0) drawArc(ring, 0f, 360f, false, tl, sz, style = Stroke(stroke))
                else {
                    var start = -90f
                    for (s in slices) {
                        if (s.value == 0) continue
                        val sweep = 360f * s.value / total
                        drawArc(s.color, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, tl, sz, style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTop, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(centerBottom, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(it.color, CircleShape))
                    Text("${it.label}  ${fmt(it.value)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Stacked bars (critical at the bottom) for the last days of alert volume. */
@Composable
fun DailyStackedBars(days: List<DayTotals>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) { Text("No data", style = MaterialTheme.typography.bodySmall); return }
    val max = days.maxOf { it.total }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.6f
            days.forEachIndexed { i, d ->
                var y = size.height
                val x = i * slot + (slot - barW) / 2
                for ((v, sev) in listOf(d.critical to "critical", d.high to "high", d.medium to "medium", d.low to "low")) {
                    if (v == 0) continue
                    val h = size.height * v / max
                    drawRoundRect(severityColor(sev), Offset(x, y - h), Size(barW, h), CornerRadius(2.dp.toPx()))
                    y -= h
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                    Text(fmt(it.total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Weekday (rows, Mon..Sun) x hour (columns, 00..23) activity intensity. */
@Composable
fun Heatmap(grid: Array<IntArray>, color: Color, modifier: Modifier = Modifier) {
    val max = grid.maxOf { it.max() }.coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(24f / 7f)) {
            val cw = size.width / 24; val ch = size.height / 7; val gap = 1.5.dp.toPx()
            for (d in 0 until 7) for (h in 0 until 24) {
                val v = grid[d][h]
                drawRoundRect(
                    if (v == 0) empty else color.copy(alpha = 0.25f + 0.75f * v / max),
                    Offset(h * cw + gap / 2, d * ch + gap / 2), Size(cw - gap, ch - gap), CornerRadius(2.dp.toPx())
                )
            }
        }
        Text("Rows Mon→Sun · columns 00h→23h · last 7 days", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BarList(items: List<NameCount>, color: Color, modifier: Modifier = Modifier, max: Int = 6) {
    val shown = items.take(max)
    if (shown.isEmpty()) { Text("No data", style = MaterialTheme.typography.bodySmall); return }
    val top = shown.maxOf { it.count }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(fmt(it.count), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(it.count.toFloat() / top).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

@Composable
fun SeverityBadge(severity: String, modifier: Modifier = Modifier) {
    Text(
        severity.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(severityColor(severity), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
