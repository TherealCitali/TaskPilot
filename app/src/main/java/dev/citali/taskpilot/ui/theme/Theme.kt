package dev.citali.taskpilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TaskPilotDarkColors = darkColorScheme(
    primary = Color(0xFFB9A4FF),
    onPrimary = Color(0xFF26145F),
    primaryContainer = Color(0xFF3B2A73),
    onPrimaryContainer = Color(0xFFE9DEFF),
    secondary = Color(0xFFD0F977),
    onSecondary = Color(0xFF263500),
    secondaryContainer = Color(0xFF3C4D08),
    onSecondaryContainer = Color(0xFFE6FF9F),
    tertiary = Color(0xFFFFB6C9),
    onTertiary = Color(0xFF4D1127),
    background = Color(0xFF0D0D16),
    onBackground = Color(0xFFE9E1F4),
    surface = Color(0xFF12121D),
    onSurface = Color(0xFFE9E1F4),
    surfaceVariant = Color(0xFF242331),
    onSurfaceVariant = Color(0xFFC8C1D2),
    outline = Color(0xFF918A9C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val TaskPilotLightColors = lightColorScheme(
    primary = Color(0xFF5E43A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF1D064F),
    secondary = Color(0xFF526A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4F68A),
    onSecondaryContainer = Color(0xFF182000),
    tertiary = Color(0xFF9C3D61),
    onTertiary = Color.White,
    background = Color(0xFFFFF9FF),
    onBackground = Color(0xFF1D1A20),
    surface = Color(0xFFFFF9FF),
    onSurface = Color(0xFF1D1A20),
    surfaceVariant = Color(0xFFE8E0EC),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A747E)
)

private val TaskPilotShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

@Composable
fun TaskPilotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme || isSystemInDarkTheme()) TaskPilotDarkColors else TaskPilotLightColors,
        typography = Typography(),
        shapes = TaskPilotShapes,
        content = content
    )
}
