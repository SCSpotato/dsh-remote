package dev.dsh.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * DeepSeek Harness design tokens (sourced from dsh-client-ui-theme
 * design-platform.css). Values are split into light / dark; the bare
 * DshXxx accessors below resolve through [LocalDshColors] so every existing
 * `color = DshAmber` style call stays theme-aware automatically.
 */
@Immutable
data class DshColors(
    val primary: Color,
    val success: Color,
    val warn: Color,
    val danger: Color,
    val userText: Color,
    val bubble: Color,
    val bubbleHighlight: Color,
    val laneInput: Color,
    val laneModel: Color,
    val laneTool: Color,
    val contextSystem: Color,
    val contextTools: Color,
    val contextMessages: Color,
)

val DshDarkColors = DshColors(
    primary = Color(0xFF679EFE),        // state-business-primary (deepseek-400)
    success = Color(0xFF22C55E),        // state-success-primary (green-500)
    warn = Color(0xFFF59E0B),           // state-warn-primary (amber-500)
    danger = Color(0xFFF25A5A),         // state-error-primary (red-400)
    userText = Color(0xFF93C5FD),       // blue-300
    bubble = Color(0xFF2C2C2E),         // neutral-bluish-850
    bubbleHighlight = Color(0xFF43454A),// neutral-bluish-750
    laneInput = Color(0xFF679EFE),
    laneModel = Color(0xFF9474BC),
    laneTool = Color(0xFFDD8629),
    contextSystem = Color(0xFFADB2B8),  // neutral-bluish-400
    contextTools = Color(0xFFA78BFA),
    contextMessages = Color(0xFF5686FE),// blue-450
)

val DshLightColors = DshColors(
    primary = Color(0xFF4176E6),        // state-business-primary (deepseek-500)
    success = Color(0xFF22C55E),
    warn = Color(0xFFF59E0B),
    danger = Color(0xFFEC1313),         // state-error-primary (red-600)
    userText = Color(0xFF2563EB),       // blue-600
    bubble = Color(0xFFEDF3FE),         // deepseek-50
    bubbleHighlight = Color(0xFFD3E2FF),// deepseek-200
    laneInput = Color(0xFF4176E6),
    laneModel = Color(0xFF7C5CD6),
    laneTool = Color(0xFFDD8629),
    contextSystem = Color(0xFF81858C),  // neutral-bluish-600
    contextTools = Color(0xFFA78BFA),
    contextMessages = Color(0xFF5686FE),
)

val LocalDshColors = staticCompositionLocalOf { DshDarkColors }

private val DarkScheme = darkColorScheme(
    primary = DshDarkColors.primary,
    background = Color(0xFF151517),     // neutral-bluish-950 (bg-base)
    surface = Color(0xFF232324),        // neutral-bluish-875 (layer-1)
    surfaceVariant = Color(0xFF353638), // neutral-bluish-800 (module platform)
    onPrimary = Color(0xFF0F1115),
    onBackground = Color(0xFFF9FAFB),   // label-primary
    onSurface = Color(0xFFF9FAFB),
    onSurfaceVariant = Color(0xFFCFD3D6),// label-secondary
    outline = Color(0xFF353638),
    error = DshDarkColors.danger,
    secondary = DshDarkColors.success,
    tertiary = DshDarkColors.warn,
)

private val LightScheme = lightColorScheme(
    primary = DshLightColors.primary,
    background = Color(0xFFFFFFFF),     // neutral-bluish-00 (bg-base)
    surface = Color(0xFFFFFFFF),        // layer-1
    surfaceVariant = Color(0xFFF5F6F7), // neutral-bluish-60 (module platform)
    onPrimary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F1115),   // label-primary
    onSurface = Color(0xFF0F1115),
    onSurfaceVariant = Color(0xFF61666B),// label-secondary
    outline = Color(0xFFE1E5EE),        // neutral-bluish-200
    error = DshLightColors.danger,
    secondary = DshLightColors.success,
    tertiary = DshLightColors.warn,
)

@Composable
fun DshTheme(preference: String = "system", content: @Composable () -> Unit) {
    val dark = when (preference) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DshDarkColors else DshLightColors
    CompositionLocalProvider(LocalDshColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
        ) {
            // Fill the whole window with the theme background so every screen
            // (settings, balance, subagents, …) follows light/dark correctly.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}

// ---- theme-aware accessors (legacy names kept for call-site compatibility) ----
val DshGreen: Color
    @Composable get() = LocalDshColors.current.success
val DshAmber: Color
    @Composable get() = LocalDshColors.current.warn
val DshRed: Color
    @Composable get() = LocalDshColors.current.danger
val DshPrimary: Color
    @Composable get() = LocalDshColors.current.primary
val DshUserText: Color
    @Composable get() = LocalDshColors.current.userText
val DshLaneInput: Color
    @Composable get() = LocalDshColors.current.laneInput
val DshLaneModel: Color
    @Composable get() = LocalDshColors.current.laneModel
val DshLaneTool: Color
    @Composable get() = LocalDshColors.current.laneTool
val DshContextSystem: Color
    @Composable get() = LocalDshColors.current.contextSystem
val DshContextTools: Color
    @Composable get() = LocalDshColors.current.contextTools
val DshContextMessages: Color
    @Composable get() = LocalDshColors.current.contextMessages
