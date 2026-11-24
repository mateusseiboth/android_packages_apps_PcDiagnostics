package org.lineageos.pcdiagnostics.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** A test state's container and the color drawn on it. */
data class StateColors(val container: Color, val onContainer: Color)

data class StatePalette(
    val success: StateColors,
    val failure: StateColors,
    val warning: StateColors,
    val inactive: StateColors,
    val progress: StateColors,
)

private val LIGHT_STATES = StatePalette(
    success = StateColors(Color(0xFFBEFFDC), Color(0xFF005237)),
    failure = StateColors(Color(0xFFFFDADA), Color(0xFF920027)),
    warning = StateColors(Color(0xFFFFDDB3), Color(0xFF624000)),
    inactive = StateColors(Color(0x1F222415), Color(0x61222415)),
    progress = StateColors(Color(0xFFDBE1FF), Color(0xFF244290)),
)

private val DARK_STATES = StatePalette(
    success = StateColors(Color(0xFF27332A), Color(0xFF89F9AF)),
    failure = StateColors(Color(0xFF332420), Color(0xFFFFB59E)),
    warning = StateColors(Color(0xFF332D1F), Color(0xFFF7BD00)),
    inactive = StateColors(Color(0x1FDBE4E6), Color(0x61DBE4E6)),
    progress = StateColors(Color(0xFF172A30), Color(0xFF75D3F0)),
)

val LocalStatePalette = staticCompositionLocalOf { LIGHT_STATES }

private class Look(val scheme: (Context) -> ColorScheme, val states: StatePalette)

/** The system's wallpaper colors, light or dark with the system setting. */
private val LOOKS = mapOf(
    false to Look(::dynamicLightColorScheme, LIGHT_STATES),
    true to Look(::dynamicDarkColorScheme, DARK_STATES),
)

@Composable
fun DiagnosticsTheme(content: @Composable () -> Unit) {
    val look = LOOKS.getValue(isSystemInDarkTheme())
    CompositionLocalProvider(LocalStatePalette provides look.states) {
        MaterialTheme(colorScheme = look.scheme(LocalContext.current), content = content)
    }
}
