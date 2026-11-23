package app.psykit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Color(0xFFB5479A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8EE),
    onPrimaryContainer = Color(0xFF3B0730),
    secondary = Color(0xFF6A4FB8),
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF22125A),
    background = Color(0xFFF6EEFB),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF1E4F0),
)

@Composable
fun PsykitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
