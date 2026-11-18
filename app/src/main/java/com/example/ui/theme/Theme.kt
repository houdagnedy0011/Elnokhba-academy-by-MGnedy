package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val EliteLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = Color.White,
    secondary = GoldRoyal,
    onSecondary = NavyDeep,
    secondaryContainer = GoldSoftContainer,
    onSecondaryContainer = GoldDarkContainer,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = EmeraldSuccess,
    background = IvoryBackground,
    onBackground = TextDark,
    surface = IvorySurface,
    onSurface = TextDark,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    outline = OutlineLight,
    outlineVariant = OutlineLight.copy(alpha = 0.5f),
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = CrimsonError
)

private val EliteDarkColorScheme = darkColorScheme(
    primary = GoldBright,
    onPrimary = NavyDeep,
    primaryContainer = NavyContainer,
    onPrimaryContainer = GoldBright,
    secondary = GoldRoyal,
    onSecondary = NavyDeep,
    secondaryContainer = GoldDarkContainer,
    onSecondaryContainer = GoldBright,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFF9DF2C9),
    background = DarkBackground,
    onBackground = TextLight,
    surface = DarkSurface,
    onSurface = TextLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextLightSecondary,
    outline = OutlineDark,
    outlineVariant = OutlineDark.copy(alpha = 0.6f),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = CrimsonContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AcademyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EliteAcademyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EliteDarkColorScheme else EliteLightColorScheme

    // فرض اتجاه الواجهة من اليمين إلى اليسار (RTL) بالكامل لكافة الشاشات والمكونات
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AcademyShapes,
            content = content
        )
    }
}
