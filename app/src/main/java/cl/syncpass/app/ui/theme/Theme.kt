package cl.syncpass.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = WarmAccent,
    onTertiary = Color.White,
    tertiaryContainer = WarmContainer,
    onTertiaryContainer = Color(0xFF2D1600),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = MistBackground,
    onBackground = Ink,
    surface = MistSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = InkMuted,
    outline = Color(0xFF6F7979),
    outlineVariant = OutlineSoft,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F7F6),
    surfaceContainer = Color(0xFFECF2F1),
    surfaceContainerHigh = Color(0xFFE6ECEB),
    surfaceContainerHighest = Color(0xFFE0E6E5)
)

private val DarkColors = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = DarkTealOnPrimary,
    primaryContainer = DarkTealPrimaryContainer,
    onPrimaryContainer = DarkTealOnPrimaryContainer,
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFE9BE8D),
    onTertiary = Color(0xFF4A2A00),
    tertiaryContainer = Color(0xFF6A4210),
    onTertiaryContainer = Color(0xFFFFDDB7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF131C1B),
    surfaceContainer = Color(0xFF172120),
    surfaceContainerHigh = Color(0xFF212B2A),
    surfaceContainerHighest = Color(0xFF2C3635)
)

private val SyncPassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SyncPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SyncPassTypography,
        shapes = SyncPassShapes,
        content = content
    )
}
