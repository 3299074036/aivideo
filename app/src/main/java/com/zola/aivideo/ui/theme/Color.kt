package com.zola.aivideo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 影译配色：M3 Expressive。
 *
 * Android 12+ 默认走 Material You 动态取色（见 Theme.kt）；
 * 以下静态配色仅作为动态取色不可用时的回退（低版本系统）。
 */
private val SeedPrimary = Color(0xFF3B5BFD)
private val SeedSecondary = Color(0xFF7C5CFF)
private val SeedTertiary = Color(0xFF00B3A6)

val LightColorScheme = lightColorScheme(
    primary = SeedPrimary,
    secondary = SeedSecondary,
    tertiary = SeedTertiary,
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9C6FF),
    secondary = Color(0xFFCDBDFF),
    tertiary = Color(0xFF5BD8CC),
)
