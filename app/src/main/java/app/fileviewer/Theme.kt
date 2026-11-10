package app.fileviewer

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

// Brand palette: the teal of the launcher icon. The window colours in res/values*/colors.xml and the page colours
// in assets/viewer.html use the same background values, so nothing flashes while a screen loads.

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E6E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFF0EC),
    onPrimaryContainer = Color(0xFF00201F),
    inversePrimary = Color(0xFF62D5CF),
    secondary = Color(0xFF4A6362),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E6),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF3E6482),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE5FF),
    onTertiaryContainer = Color(0xFF001D32),
    background = Color(0xFFF4F8F8),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4F8F8),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    surfaceBright = Color(0xFFF4F8F8),
    surfaceDim = Color(0xFFD5DBDB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF4F3),
    surfaceContainer = Color(0xFFE8EFEE),
    surfaceContainerHigh = Color(0xFFE2E9E8),
    surfaceContainerHighest = Color(0xFFDDE4E3)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF62D5CF),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504E),
    onPrimaryContainer = Color(0xFFBFF0EC),
    inversePrimary = Color(0xFF0B6E6E),
    secondary = Color(0xFFB0CCCA),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4A),
    onSecondaryContainer = Color(0xFFCCE8E6),
    tertiary = Color(0xFFA5CAEE),
    onTertiary = Color(0xFF0A334F),
    tertiaryContainer = Color(0xFF254A67),
    onTertiaryContainer = Color(0xFFCDE5FF),
    background = Color(0xFF0B1313),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0B1313),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3231),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4948),
    surfaceBright = Color(0xFF2A3434),
    surfaceDim = Color(0xFF0B1313),
    surfaceContainerLowest = Color(0xFF070E0E),
    surfaceContainerLow = Color(0xFF111A1A),
    surfaceContainer = Color(0xFF151F1F),
    surfaceContainerHigh = Color(0xFF1F2929),
    surfaceContainerHighest = Color(0xFF2A3434)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/** Colour of list cards: white in light mode, a slightly lifted surface in dark mode. */
@Composable
fun cardColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.surfaceContainerLowest
