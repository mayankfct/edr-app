package com.forensiccybertech.eagleye.ui.theme

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { System, Light, Dark }

/** Remembers the user's theme choice (not sensitive, so plain SharedPreferences is fine). */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.System)
    )
    val mode: StateFlow<ThemeMode> = _mode

    fun set(m: ThemeMode) { _mode.value = m; prefs.edit().putString("theme", m.name).apply() }
}

@Immutable data class SevColors(val fg: Color, val bg: Color, val border: Color)

/** Design-system tokens that Material3's ColorScheme has no slot for (EaglEye Sentinel design). */
@Immutable
class EagleColors(
    val canvas: Color, val card: Color, val border: Color, val chip: Color,
    val mono: Color, val monoText: Color, val glow: Color, val success: Color, val warning: Color,
    val critical: SevColors, val high: SevColors, val medium: SevColors, val low: SevColors, val info: SevColors,
) {
    fun severity(s: String): SevColors = when (s.lowercase()) {
        "critical", "crit" -> critical; "high" -> high; "medium", "med" -> medium; "low" -> low; else -> info
    }
}

private val DarkEagle = EagleColors(
    canvas = Color(0xFF0B111E), card = Color(0xFF162238), border = Color(0xFF223656), chip = Color(0xFF1C2B45),
    mono = Color(0xFF0B111E), monoText = Color(0xFF94A3B8), glow = Color(0xFF00E5FF), success = Color(0xFF10B981), warning = Color(0xFFF59E0B),
    critical = SevColors(Color(0xFFEF4444), Color(0xFF450A0A), Color(0xFF7F1D1D)),
    high = SevColors(Color(0xFFF97316), Color(0xFF431407), Color(0xFF7C2D12)),
    medium = SevColors(Color(0xFFF59E0B), Color(0xFF451A03), Color(0xFF78350F)),
    low = SevColors(Color(0xFF10B981), Color(0xFF064E3B), Color(0xFF065F46)),
    info = SevColors(Color(0xFF94A3B8), Color(0xFF1E293B), Color(0xFF334155)),
)

private val LightEagle = EagleColors(
    canvas = Color(0xFFF1F5F9), card = Color.White, border = Color(0xFFD5DEE8), chip = Color(0xFFE6EDF4),
    mono = Color(0xFFEEF3F8), monoText = Color(0xFF475569), glow = Color(0xFF0891B2), success = Color(0xFF047857), warning = Color(0xFFB45309),
    critical = SevColors(Color(0xFFB91C1C), Color(0xFFFEE2E2), Color(0xFFFCA5A5)),
    high = SevColors(Color(0xFFC2410C), Color(0xFFFFEDD5), Color(0xFFFDBA74)),
    medium = SevColors(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFFFCD34D)),
    low = SevColors(Color(0xFF047857), Color(0xFFD1FAE5), Color(0xFF6EE7B7)),
    info = SevColors(Color(0xFF475569), Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF1FB6D6), onPrimary = Color(0xFF00252D),
    secondary = Color(0xFF6366F1), onSecondary = Color.White,
    tertiary = Color(0xFF00E5FF), onTertiary = Color(0xFF00363D),
    background = Color(0xFF0B111E), onBackground = Color(0xFFD4E4FA),
    surface = Color(0xFF131D31), onSurface = Color(0xFFD4E4FA),
    surfaceVariant = Color(0xFF1C2B45), onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF162238), surfaceContainerHigh = Color(0xFF1C2B45),
    outline = Color(0xFF223656), outlineVariant = Color(0xFF223656),
    error = Color(0xFFEF4444), errorContainer = Color(0xFF450A0A), onErrorContainer = Color(0xFFFFDAD6),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF007C95), onPrimary = Color.White,
    secondary = Color(0xFF4F46E5), tertiary = Color(0xFF0891B2),
    background = Color(0xFFF1F5F9), surface = Color.White,
    surfaceVariant = Color(0xFFE6EDF4), onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE6EDF4),
    outline = Color(0xFFD5DEE8), outlineVariant = Color(0xFFD5DEE8),
)

private val LocalEagle = staticCompositionLocalOf { DarkEagle }

object Eagle {
    val colors: EagleColors @Composable @ReadOnlyComposable get() = LocalEagle.current
}

/** Forensic identifiers (hashes, IPs, PIDs, commands) always use monospace. */
val MonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
val MonoMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
fun EagleEyeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEagle provides if (darkTheme) DarkEagle else LightEagle) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}
