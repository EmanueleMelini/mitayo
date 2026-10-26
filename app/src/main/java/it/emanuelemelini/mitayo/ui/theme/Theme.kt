package it.emanuelemelini.mitayo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.emanuelemelini.mitayo.R

/** Brand and action colors, the same in both themes: every swipe direction has its own. */
object MitayoColors {
    val Murasaki = Color(0xFF7C5CFF)
    val Sakura = Color(0xFFFF7AC6)
    /** Swipe right: completed. */
    val Matcha = Color(0xFF34D399)
    val OnMatcha = Color(0xFF06281C)
    /** Swipe left: skip. */
    val Aka = Color(0xFFFF5A5F)
    /** Swipe up: watching, with the episodes seen. */
    val Kin = Color(0xFFFFC24B)
    val OnKin = Color(0xFF3A2800)
    /** Dark backdrop behind the text on the posters, in both themes. */
    val Sumi = Color(0xFF0E0C15)
}

// Every Material role is set, so nothing falls back to the library's default purple
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D85FF),
    onPrimary = Color(0xFF1E0E5C),
    primaryContainer = Color(0xFF4B33B8),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = MitayoColors.Sakura,
    onSecondary = Color(0xFF3D0026),
    secondaryContainer = Color(0xFF6B1F4C),
    onSecondaryContainer = Color(0xFFFFD8EB),
    tertiary = MitayoColors.Kin,
    onTertiary = MitayoColors.OnKin,
    tertiaryContainer = Color(0xFF5C4100),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = MitayoColors.Aka,
    onError = Color(0xFF410006),
    errorContainer = Color(0xFF7A1C22),
    onErrorContainer = Color(0xFFFFDAD8),
    background = MitayoColors.Sumi,
    onBackground = Color(0xFFF3F0FA),
    surface = MitayoColors.Sumi,
    onSurface = Color(0xFFF3F0FA),
    surfaceVariant = Color(0xFF231F2E),
    onSurfaceVariant = Color(0xFFA39DB5),
    outline = Color(0xFF4A4459),
    outlineVariant = Color(0xFF2F2A3D),
    inverseSurface = Color(0xFFF3F0FA),
    inverseOnSurface = Color(0xFF1B1824),
    inversePrimary = Color(0xFF6A45F5),
    surfaceContainerLowest = Color(0xFF09080E),
    surfaceContainerLow = Color(0xFF131119),
    surfaceContainer = Color(0xFF18151F),
    surfaceContainerHigh = Color(0xFF201C29),
    surfaceContainerHighest = Color(0xFF2A2536),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A45F5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1E0E5C),
    secondary = Color(0xFFC2307E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8EB),
    onSecondaryContainer = Color(0xFF3D0026),
    tertiary = Color(0xFF8A6100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = MitayoColors.OnKin,
    error = Color(0xFFD7263D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410006),
    background = Color(0xFFF7F5FB),
    onBackground = Color(0xFF17141F),
    surface = Color(0xFFF7F5FB),
    onSurface = Color(0xFF17141F),
    surfaceVariant = Color(0xFFE7E2F1),
    onSurfaceVariant = Color(0xFF5A5468),
    outline = Color(0xFF7A7389),
    outlineVariant = Color(0xFFCFC8DC),
    inverseSurface = Color(0xFF2C2936),
    inverseOnSurface = Color(0xFFF3F0FA),
    inversePrimary = Color(0xFF9D85FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F0F9),
    surfaceContainer = Color(0xFFEDE9F5),
    surfaceContainerHigh = Color(0xFFE7E2F1),
    surfaceContainerHighest = Color(0xFFE1DBEC),
)

/**
 * M PLUS Rounded 1c (SIL OFL, see licenses/): Latin and kana only, from Google Fonts. Characters
 * it lacks (e.g. kanji in a title) fall back to the system font.
 */
private val MPlusRounded = FontFamily(
    Font(R.font.mplus_rounded_bold, FontWeight.Bold),
    Font(R.font.mplus_rounded_extrabold, FontWeight.ExtraBold),
)

// Rounded font for titles and buttons, system font for body text and small labels
private val MitayoTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.ExtraBold),
        displayMedium = displayMedium.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.ExtraBold),
        displaySmall = displaySmall.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.ExtraBold),
        headlineLarge = headlineLarge.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.ExtraBold),
        headlineMedium = headlineMedium.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.ExtraBold),
        headlineSmall = headlineSmall.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.Bold),
        // Buttons
        labelLarge = labelLarge.copy(fontFamily = MPlusRounded, fontWeight = FontWeight.Bold),
    )
}

/** No dynamic colors: Mitayo keeps its own palette on every device. */
@Composable
fun MitayoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MitayoTypography,
        content = content,
    )
}
