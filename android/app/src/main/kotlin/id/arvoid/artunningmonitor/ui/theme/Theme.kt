package id.arvoid.artunningmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema ArTunning Monitor berbasis NomadKit.
 * - Light: token persis dari docs/design/nomadkit.md.
 * - Dark: turunan (surface/background digelapkan, warna makna disesuaikan) —
 *   ditandai derived, bukan bagian dari DESIGN.md.
 */
private val LightColors = lightColorScheme(
    primary = Sand,
    onPrimary = OnSand,
    secondary = Ocean,
    onSecondary = SurfaceDefault,
    tertiary = Forest,
    onTertiary = SurfaceDefault,
    background = NomadBackground,
    onBackground = OnLight,
    surface = SurfaceDefault,
    onSurface = OnLight,
    surfaceVariant = NomadBackground,
    onSurfaceVariant = OnLightMuted,
    outline = OutlineWarm,
    error = ErrorRed,
    onError = SurfaceDefault,
)

private val DarkColors = darkColorScheme(
    primary = Sand,
    onPrimary = OnSand,
    secondary = OceanLight,
    onSecondary = OnSand,
    tertiary = ForestLight,
    onTertiary = SurfaceDefault,
    background = DarkBackground,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = OnDarkMuted,
    outline = OutlineDark,
    error = ErrorRedLight,
    onError = OnSand,
)

@Composable
fun ArTunningTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NomadTypography,
        content = content,
    )
}
