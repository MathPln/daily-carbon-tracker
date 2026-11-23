package dte.masteriot.daily_carbon_tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Couleurs des statuts du budget carbone (dans le budget / proche de la limite / dépassé). */
@Immutable
data class StatusColors(val ok: Color, val warning: Color, val over: Color)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(StatusOkLight, StatusWarningLight, StatusOverLight)
}

/** true quand le thème haut contraste est actif (ajoute par exemple des bordures aux cartes). */
val LocalHighContrast = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    onSurfaceVariant = Color(0xFF49454F),
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    onSecondary = Teal10,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
)

/** Haut contraste clair : noir sur blanc, aucune nuance de gris pour le texte. */
private val HighContrastLightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.Black,
    onPrimaryContainer = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color.Black,
    onSecondaryContainer = Color.White,
    tertiary = Color.Black,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color.Black,
    outlineVariant = HcGrey,
)

/** Haut contraste sombre : blanc et jaune sur noir. */
private val HighContrastDarkColors = darkColorScheme(
    primary = HcYellow,
    onPrimary = Color.Black,
    primaryContainer = HcYellow,
    onPrimaryContainer = Color.Black,
    secondary = HcYellow,
    onSecondary = Color.Black,
    secondaryContainer = HcYellow,
    onSecondaryContainer = Color.Black,
    tertiary = HcYellow,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color.Black,
    onSurfaceVariant = Color.White,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    outline = Color.White,
    outlineVariant = HcGrey,
)

/**
 * Thème de l'application. Les couleurs dynamiques (Android 12+) sont volontairement
 * désactivées pour garder des contrastes maîtrisés.
 */
@Composable
fun DailyCarbonTheme(
    highContrast: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        highContrast && darkTheme -> HighContrastDarkColors
        highContrast -> HighContrastLightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    val statusColors = when {
        highContrast && darkTheme -> StatusColors(StatusOkHcDark, StatusWarningHcDark, StatusOverHcDark)
        highContrast -> StatusColors(StatusOkHcLight, StatusWarningHcLight, StatusOverHcLight)
        darkTheme -> StatusColors(StatusOkDark, StatusWarningDark, StatusOverDark)
        else -> StatusColors(StatusOkLight, StatusWarningLight, StatusOverLight)
    }

    CompositionLocalProvider(
        LocalStatusColors provides statusColors,
        LocalHighContrast provides highContrast,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
