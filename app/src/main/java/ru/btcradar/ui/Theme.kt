package ru.btcradar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Палитра «BTC/RUB Radar»: холодные сине-серые нейтрали + оранжевый акцент.
 * Акцент живёт в colorScheme.primary. Семантические цвета ниже подобраны
 * так, чтобы читаться и на светлой, и на тёмной теме.
 */
val Good = Color(0xFF1FA971)
val Bad = Color(0xFFE5534B)
val Warn = Color(0xFFD99A1E)
val TorPurple = Color(0xFF8B5CF6)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF4A033),
    onPrimary = Color(0xFF14100A),
    primaryContainer = Color(0xFF2A2114),
    onPrimaryContainer = Color(0xFFFFD9A8),
    secondary = Color(0xFF8B98A9),
    onSecondary = Color(0xFF0C1118),
    secondaryContainer = Color(0xFF1D2531),
    onSecondaryContainer = Color(0xFFE7EDF5),
    tertiary = TorPurple,
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF0C1118),
    onBackground = Color(0xFFE7EDF5),
    surface = Color(0xFF151C26),
    onSurface = Color(0xFFE7EDF5),
    surfaceVariant = Color(0xFF1D2531),
    onSurfaceVariant = Color(0xFF8B98A9),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF3A4555),
    outlineVariant = Color(0xFF28313E),
    surfaceContainerLowest = Color(0xFF0F151D),
    surfaceContainerLow = Color(0xFF131A23),
    surfaceContainer = Color(0xFF151C26),
    surfaceContainerHigh = Color(0xFF18202A),
    surfaceContainerHighest = Color(0xFF1A222D),
    error = Bad,
    onError = Color(0xFF14100A),
    errorContainer = Color(0xFF3A1714),
    onErrorContainer = Color(0xFFFFDAD5)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFD9780A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBEDDC),
    onPrimaryContainer = Color(0xFF5A3100),
    secondary = Color(0xFF5A6676),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9EEF5),
    onSecondaryContainer = Color(0xFF131A24),
    tertiary = Color(0xFF6B3FC6),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F5F9),
    onBackground = Color(0xFF131A24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF131A24),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF5A6676),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFB9C4D2),
    outlineVariant = Color(0xFFD7DFE9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    error = Color(0xFFBE3227),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3E0),
    onErrorContainer = Color(0xFF5C120C)
)

private val RadarShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
        shapes = RadarShapes,
        typography = Typography(),
        content = content
    )
}
