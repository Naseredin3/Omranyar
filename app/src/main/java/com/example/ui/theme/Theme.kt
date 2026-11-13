package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = LuminousTurquoise,
    onPrimary = Color(0xFF002026),
    primaryContainer = DarkTurquoiseContainer,
    onPrimaryContainer = Color(0xFFBDF2FA),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF0A223D),
    secondaryContainer = Color(0xFF1B3452),
    onSecondaryContainer = Color(0xFFD6E6FA),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3D2600),
    tertiaryContainer = Color(0xFF523504),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = DarkBlueprintBg,
    onBackground = DarkTextPrimary,
    surface = DarkBlueprintSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkBlueprintCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1F3540),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTealPrimary,
    onPrimary = Color.White,
    primaryContainer = SoftTurquoiseContainer,
    onPrimaryContainer = OnTurquoiseContainer,
    secondary = IsfahanCobalt,
    onSecondary = Color.White,
    secondaryContainer = SoftCobaltContainer,
    onSecondaryContainer = OnCobaltContainer,
    tertiary = IsfahanOchre,
    onTertiary = Color.White,
    tertiaryContainer = SoftOchreContainer,
    onTertiaryContainer = OnOchreContainer,
    background = WarmLimestone,
    onBackground = InkCharcoal,
    surface = CardWhite,
    onSurface = InkCharcoal,
    surfaceVariant = WarmLimestoneSurface,
    onSurfaceVariant = InkMuted,
    outline = SandOutline,
    outlineVariant = Color(0xFFECE5D8),
    error = PriorityUrgentRed,
    onError = Color.White
)

private val OmranShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OmranYarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = OmranShapes,
        content = content
    )
}
