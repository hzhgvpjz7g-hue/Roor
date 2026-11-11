package ru.btcradar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Good = Color(0xFF2FBF71)
val Bad = Color(0xFFF2544B)
val Warn = Color(0xFFE2B33C)
val TorPurple = Color(0xFF7D4CDB)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4C9AFF),
    onPrimary = Color(0xFF06121F),
    secondary = Color(0xFF8A94A6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF1E232C),
    onSurfaceVariant = Color(0xFFA7B0BF),
    outline = Color(0xFF2C333F),
    error = Bad
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1668D6),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEFF3),
    error = Bad
)

/** Моноширинный для цифр — курсы в колонках должны выравниваться по разрядам. */
val MonoNumber = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp
)

val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp
)

@Composable
fun BtcRadarTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
