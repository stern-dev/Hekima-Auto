package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

data class HekimaTelemetryColors(
  val positive: Color,
  val warning: Color,
  val negative: Color,
  val accentCyan: Color,
  val cardBorder: Color,
  val chartPalette: List<Color>
)

val LocalTelemetryColors = staticCompositionLocalOf {
  HekimaTelemetryColors(
    positive = EmeraldGain,
    warning = AmberCaution,
    negative = CoralAlert,
    accentCyan = SkyCyan,
    cardBorder = NavyBorder,
    chartPalette = listOf(CobaltBlue, SkyCyan, EmeraldGain, AmberCaution, Color(0xFFA78BFA), Color(0xFFF472B6))
  )
}

private val DarkColorScheme = darkColorScheme(
  primary = CobaltBlue,
  onPrimary = Color.White,
  primaryContainer = NavySurfaceHigh,
  onPrimaryContainer = CobaltBright,
  secondary = SkyCyan,
  onSecondary = NavyObsidian,
  secondaryContainer = NavySurfaceElevated,
  onSecondaryContainer = DarkOnCanvas,
  tertiary = EmeraldGain,
  onTertiary = NavyObsidian,
  background = NavyObsidian,
  onBackground = DarkOnCanvas,
  surface = NavySurface,
  onSurface = DarkOnCanvas,
  surfaceVariant = NavySurfaceElevated,
  onSurfaceVariant = DarkOnSurfaceMuted,
  outline = NavyBorder,
  outlineVariant = NavyBorder,
  error = CoralAlert,
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = DeepCobalt,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDBEAFE),
  onPrimaryContainer = Color(0xFF1E3A8A),
  secondary = Color(0xFF0284C7),
  onSecondary = Color.White,
  secondaryContainer = SlateSurfaceVariant,
  onSecondaryContainer = LightInk,
  tertiary = Color(0xFF059669),
  onTertiary = Color.White,
  background = SlateAlabaster,
  onBackground = LightInk,
  surface = PureWhite,
  onSurface = LightInk,
  surfaceVariant = SlateSurfaceVariant,
  onSurfaceVariant = LightInkMuted,
  outline = SlateBorder,
  outlineVariant = SlateBorder,
  error = Color(0xFFDC2626),
  onError = Color.White
)

private val HekimaShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp)
)

@Composable
fun HekimaAutoTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit
) {
  val useDark = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
  }

  val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
  val telemetry = if (useDark) {
    HekimaTelemetryColors(
      positive = EmeraldGain,
      warning = AmberCaution,
      negative = CoralAlert,
      accentCyan = SkyCyan,
      cardBorder = NavyBorder,
      chartPalette = listOf(CobaltBlue, SkyCyan, EmeraldGain, AmberCaution, Color(0xFFA78BFA), Color(0xFFF472B6))
    )
  } else {
    HekimaTelemetryColors(
      positive = Color(0xFF059669),
      warning = Color(0xFFD97706),
      negative = Color(0xFFDC2626),
      accentCyan = Color(0xFF0284C7),
      cardBorder = SlateBorder,
      chartPalette = listOf(DeepCobalt, Color(0xFF0284C7), Color(0xFF059669), Color(0xFFD97706), Color(0xFF7C3AED), Color(0xFFDB2777))
    )
  }

  CompositionLocalProvider(LocalTelemetryColors provides telemetry) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = HekimaTypography,
      shapes = HekimaShapes,
      content = content
    )
  }
}
