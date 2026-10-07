package net.marvinweber.simsli.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val DarkColorScheme = ColorScheme(
    primary = Color(0xFF81C784),          // Subtle soft green
    primaryContainer = Color(0xFF1E2822), // Dark forest charcoal
    onPrimary = Color.Black,
    onPrimaryContainer = Color(0xFFC8E6C9),

    secondary = Color(0xFFA5D6A7),
    secondaryContainer = Color(0xFF212628), // Dark slate
    onSecondary = Color.Black,
    onSecondaryContainer = Color(0xFFE0E0E0),

    tertiary = Color(0xFF80CBC4),
    tertiaryContainer = Color(0xFF1C2826),
    onTertiary = Color.Black,
    onTertiaryContainer = Color(0xFFB2DFDB),

    surfaceContainer = Color(0xFF1C1C1E),     // Dark Apple/Wear dark card grey
    surfaceContainerHigh = Color(0xFF2C2C2E), // Highlighted card
    surfaceContainerLow = Color(0xFF121214),

    onSurface = Color(0xFFF2F2F7),            // Crisp off-white text
    onSurfaceVariant = Color(0xFFAAAAAA),     // Readable muted secondary text

    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF28282A),

    background = Color.Black,
    onBackground = Color.White,

    error = Color(0xFFEF5350),
    onError = Color.Black,
    errorContainer = Color(0xFF3E1B1B),
    onErrorContainer = Color(0xFFFFCDD2)
)

@Composable
fun SimsliWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
