package pl.walle.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Barwy z filmu: żółty lakier, rdza, piaskowa Ziemia i niebieskie oczy. */
object WalleColors {
    val Yellow = Color(0xFFE8A91F)
    val Rust = Color(0xFF9A4F12)
    val EyeBlue = Color(0xFF2A8FD6)
    val Ink = Color(0xFF2B2118)
    val Ok = Color(0xFF2E9E5B)
    val Warn = Color(0xFFE8A91F)
    val Bad = Color(0xFFC62828)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A4F12),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB8),
    onPrimaryContainer = Color(0xFF321300),
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE29A),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF176FA8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDE5FF),
    onTertiaryContainer = Color(0xFF001D33),
    background = Color(0xFFFFF8EF),
    onBackground = Color(0xFF211A14),
    surface = Color(0xFFFFF8EF),
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFF1E0CE),
    onSurfaceVariant = Color(0xFF51443A),
    surfaceContainerLow = Color(0xFFFAEFE0),
    surfaceContainer = Color(0xFFF6EAD8),
    surfaceContainerHigh = Color(0xFFF0E3D0),
    outline = Color(0xFF84746A),
    outlineVariant = Color(0xFFD6C3B4),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB77A),
    onPrimary = Color(0xFF4F2500),
    primaryContainer = Color(0xFF703900),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFF2C14A),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE29A),
    tertiary = Color(0xFF8DCDFF),
    onTertiary = Color(0xFF00344F),
    tertiaryContainer = Color(0xFF004B71),
    onTertiaryContainer = Color(0xFFCDE5FF),
    background = Color(0xFF1C1511),
    onBackground = Color(0xFFEDE0D6),
    surface = Color(0xFF1C1511),
    onSurface = Color(0xFFEDE0D6),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD6C3B4),
    surfaceContainerLow = Color(0xFF241C16),
    surfaceContainer = Color(0xFF2B221B),
    surfaceContainerHigh = Color(0xFF362C24),
    outline = Color(0xFF9E8D80),
    outlineVariant = Color(0xFF51443A),
    error = Color(0xFFFFB4AB),
)

private val WalleShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Jawna rodzina bezszeryfowa, żeby nie dziedziczyć dziwnych czcionek systemowych telefonu. */
private fun Typography.withSans(): Typography {
    fun TextStyle.sans() = copy(fontFamily = FontFamily.SansSerif)
    return copy(
        displayLarge = displayLarge.sans(), displayMedium = displayMedium.sans(), displaySmall = displaySmall.sans(),
        headlineLarge = headlineLarge.sans(), headlineMedium = headlineMedium.sans(), headlineSmall = headlineSmall.sans(),
        titleLarge = titleLarge.sans(), titleMedium = titleMedium.sans(), titleSmall = titleSmall.sans(),
        bodyLarge = bodyLarge.sans(), bodyMedium = bodyMedium.sans(), bodySmall = bodySmall.sans(),
        labelLarge = labelLarge.sans(), labelMedium = labelMedium.sans(), labelSmall = labelSmall.sans(),
    )
}

@Composable
fun WalleTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = WalleShapes,
        typography = Typography().withSans(),
        content = content,
    )
}
