package ch.leftclick.piflash.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import ch.leftclick.piflash.domain.prefs.Accent

val Raspberry = Color(0xFFC51A4A)
val Bolt = Color(0xFFF5C518)

fun accentSwatch(accent: Accent): Color = when (accent) {
    Accent.DYNAMIC -> Color(0xFF9E9E9E)
    Accent.RASPBERRY -> Raspberry
    Accent.TEAL -> Color(0xFF0F7A7A)
    Accent.INDIGO -> Color(0xFF3F51B5)
    Accent.AMBER -> Color(0xFFC79212)
    Accent.FOREST -> Color(0xFF2E6B3A)
}

fun accentScheme(accent: Accent, dark: Boolean): ColorScheme = when (accent) {
    Accent.DYNAMIC -> if (dark) darkColorScheme() else lightColorScheme()
    Accent.RASPBERRY -> if (dark) raspberryDark() else raspberryLight()
    Accent.TEAL -> if (dark) tealDark() else tealLight()
    Accent.INDIGO -> if (dark) indigoDark() else indigoLight()
    Accent.AMBER -> if (dark) amberDark() else amberLight()
    Accent.FOREST -> if (dark) forestDark() else forestLight()
}

private fun raspberryLight() = lightColorScheme(
    primary = Raspberry,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF3F0014),
    secondary = Color(0xFF8B6914),
    onSecondary = Color.White,
    tertiary = Color(0xFF6B4F00)
)

private fun raspberryDark() = darkColorScheme(
    primary = Color(0xFFFFB2C0),
    onPrimary = Color(0xFF650028),
    primaryContainer = Color(0xFF920038),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Bolt,
    onSecondary = Color(0xFF3D2E00),
    tertiary = Color(0xFFE8C349)
)

private fun tealLight() = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF1F0),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    tertiary = Color(0xFF4A607C)
)

private fun tealDark() = darkColorScheme(
    primary = Color(0xFF80D5D4),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF005050),
    onPrimaryContainer = Color(0xFF9DF1F0),
    secondary = Color(0xFFB0CCCC),
    tertiary = Color(0xFFB2C8E8)
)

private fun indigoLight() = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF5B5E72),
    tertiary = Color(0xFF77536D)
)

private fun indigoDark() = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF1A2578),
    primaryContainer = Color(0xFF333C9C),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C6DC),
    tertiary = Color(0xFFE6BAD7)
)

private fun amberLight() = lightColorScheme(
    primary = Color(0xFF7B5800),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA3),
    onPrimaryContainer = Color(0xFF271900),
    secondary = Color(0xFF6B5D3F),
    tertiary = Color(0xFF4A6546)
)

private fun amberDark() = darkColorScheme(
    primary = Bolt,
    onPrimary = Color(0xFF412D00),
    primaryContainer = Color(0xFF5C4200),
    onPrimaryContainer = Color(0xFFFFDEA3),
    secondary = Color(0xFFD8C4A0),
    tertiary = Color(0xFFB1CFA9)
)

private fun forestLight() = lightColorScheme(
    primary = Color(0xFF2E6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1B8),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF516351),
    tertiary = Color(0xFF39656D)
)

private fun forestDark() = darkColorScheme(
    primary = Color(0xFF98D49D),
    onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF145224),
    onPrimaryContainer = Color(0xFFB4F1B8),
    secondary = Color(0xFFB8CCB6),
    tertiary = Color(0xFFA1CED6)
)
