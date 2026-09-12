package jp.yagi.safecleaner.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8BF8C7),
    secondary = Color(0xFF4D6358),
    tertiary = Color(0xFF3D6373),
    surface = Color(0xFFF6FBF6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DDBAC),
    primaryContainer = Color(0xFF005138),
    secondary = Color(0xFFB4CCBF),
    tertiary = Color(0xFFA5CDDF),
)

@Composable
fun SafeCleanerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
