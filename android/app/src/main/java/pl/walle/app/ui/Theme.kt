package pl.walle.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta z bajki: żółta blacha, rdza, piasek pustyni, niebieskie oczy.
val WalleYellow = Color(0xFFE0A526)
val WalleRust = Color(0xFF8B4A1F)
val WalleDark = Color(0xFF2B2118)
val WalleSand = Color(0xFFF6E7C1)
val WalleEyeBlue = Color(0xFF3E8FC4)
val WalleDanger = Color(0xFFC62828)

private val colors = lightColorScheme(
    primary = WalleYellow,
    onPrimary = WalleDark,
    primaryContainer = Color(0xFFFBE3A0),
    onPrimaryContainer = WalleDark,
    secondary = WalleRust,
    onSecondary = Color.White,
    tertiary = WalleEyeBlue,
    background = WalleSand,
    onBackground = WalleDark,
    surface = WalleSand,
    onSurface = WalleDark,
    surfaceVariant = Color(0xFFE2CFA0),
    onSurfaceVariant = WalleDark,
    outline = WalleRust,
)

// Czcionka monospace nawiązuje do napisów na obudowie robota.
private val typography = Typography().let {
    it.copy(
        titleLarge = it.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        titleMedium = it.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

@Composable
fun WalleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
