package com.forensiccybertech.eagleye.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(primary = Color(0xFF1FB6D6), background = Color(0xFF0B1B2B), surface = Color(0xFF12283C))
private val Light = lightColorScheme(primary = Color(0xFF007C95))

@Composable
fun EagleEyeTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
