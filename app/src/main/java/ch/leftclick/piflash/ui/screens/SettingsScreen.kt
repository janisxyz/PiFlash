package ch.leftclick.piflash.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.leftclick.piflash.domain.prefs.Accent
import ch.leftclick.piflash.domain.prefs.ThemeMode
import ch.leftclick.piflash.ui.i18n.AppLanguages
import ch.leftclick.piflash.ui.i18n.LocalUiText
import ch.leftclick.piflash.ui.theme.accentSwatch
import ch.leftclick.piflash.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onLanguage: (String) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (Accent) -> Unit,
    onBack: () -> Unit
) {
    val t = LocalUiText.current
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(t.settings) },
            navigationIcon = { TextButton(onClick = onBack) { Text(t.back) } }
        )
    }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(t.language, style = MaterialTheme.typography.titleSmall)
            Text(
                t.languageHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguages.forEach { lang ->
                    val label = if (lang.tag.isEmpty()) t.languageSystem else lang.nativeName
                    FilterChip(
                        selected = state.languageTag == lang.tag,
                        onClick = { onLanguage(lang.tag) },
                        label = { Text(label) }
                    )
                }
            }

            Text(t.appearance, style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeMode(ThemeMode.SYSTEM) },
                    label = { Text(t.appearanceSystem) }
                )
                FilterChip(
                    selected = state.themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeMode(ThemeMode.LIGHT) },
                    label = { Text(t.appearanceLight) }
                )
                FilterChip(
                    selected = state.themeMode == ThemeMode.DARK,
                    onClick = { onThemeMode(ThemeMode.DARK) },
                    label = { Text(t.appearanceDark) }
                )
            }

            Text(t.color, style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dynamicOk) {
                    AccentDot(
                        selected = state.accent == Accent.DYNAMIC,
                        label = t.colorDynamic,
                        dynamic = true,
                        color = accentSwatch(Accent.RASPBERRY),
                        onClick = { onAccent(Accent.DYNAMIC) }
                    )
                }
                listOf(
                    Accent.RASPBERRY to t.colorRaspberry,
                    Accent.TEAL to t.colorTeal,
                    Accent.INDIGO to t.colorIndigo,
                    Accent.AMBER to t.colorAmber,
                    Accent.FOREST to t.colorForest
                ).forEach { (accent, label) ->
                    AccentDot(
                        selected = state.accent == accent,
                        label = label,
                        dynamic = false,
                        color = accentSwatch(accent),
                        onClick = { onAccent(accent) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                t.settingsStayOnPhone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccentDot(
    selected: Boolean,
    label: String,
    dynamic: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val border = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (dynamic) {
                        Modifier.background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFC51A4A),
                                    Color(0xFF3F51B5),
                                    Color(0xFF0F7A7A),
                                    Color(0xFFF5C518),
                                    Color(0xFFC51A4A)
                                )
                            )
                        )
                    } else {
                        Modifier.background(color)
                    }
                )
                .border(if (selected) 3.dp else 1.dp, border, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }
}
