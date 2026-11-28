package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.RandomTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemePreset
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_font_extra_large
import syncclip.feature.settings.impl.generated.resources.settings_font_large
import syncclip.feature.settings.impl.generated.resources.settings_font_medium
import syncclip.feature.settings.impl.generated.resources.settings_font_small
import syncclip.feature.settings.impl.generated.resources.settings_mode_dark
import syncclip.feature.settings.impl.generated.resources.settings_mode_light
import syncclip.feature.settings.impl.generated.resources.settings_mode_system
import syncclip.feature.settings.impl.generated.resources.settings_palette_aqua
import syncclip.feature.settings.impl.generated.resources.settings_palette_dynamic
import syncclip.feature.settings.impl.generated.resources.settings_palette_monochrome
import syncclip.feature.settings.impl.generated.resources.settings_palette_random
import syncclip.feature.settings.impl.generated.resources.settings_palette_regenerate
import syncclip.feature.settings.impl.generated.resources.settings_palette_winter

/**
 * Секция «Внешний вид»: режим, палитра (включая Random, US-015) и размер
 * шрифта (US-011). Всё применяется мгновенно — экран только сообщает о выборе.
 */
@Composable
internal fun SettingsAppearanceSection(
    theme: ThemeState,
    fontScale: FontScale,
    onThemeChange: (ThemeState) -> Unit,
    onRegenerateRandomTheme: () -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
    ) {
        ThemeModeSelector(
            mode = theme.mode,
            onModeChange = { mode -> onThemeChange(theme.copy(mode = mode)) },
        )
        ThemePresetPicker(
            theme = theme,
            onPresetChange = { preset ->
                if (preset == ThemePreset.RANDOM && theme.preset != ThemePreset.RANDOM) {
                    // Первый выбор «Random» сразу генерирует палитру.
                    onRegenerateRandomTheme()
                } else {
                    onThemeChange(theme.copy(preset = preset))
                }
            },
        )
        if (theme.preset == ThemePreset.RANDOM) {
            OutlinedButton(
                onClick = onRegenerateRandomTheme,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SyncClipIcon(
                    kind = SyncClipIconKind.SYNC,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 14.dp,
                )
                Text(
                    text = stringResource(Res.string.settings_palette_regenerate),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        FontSizeSelector(selected = fontScale, onSelect = onFontScaleChange)
    }
}

@Composable
private fun ThemeModeSelector(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { entry ->
            val selected = entry == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (selected) scheme.surface else Color.Transparent)
                    .clickable { onModeChange(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ThemeModeLabel(entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ThemeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(Res.string.settings_mode_system)
    ThemeMode.LIGHT -> stringResource(Res.string.settings_mode_light)
    ThemeMode.DARK -> stringResource(Res.string.settings_mode_dark)
}

/**
 * Палитры в `Grid` (адаптивный шаг 4.2): число колонок зависит от доступной
 * ширины, поэтому на телефоне плитки не сжимаются в неразборчивую строку, а на
 * широком окне не растягиваются на всю ширину.
 */
@OptIn(ExperimentalGridApi::class)
@Composable
private fun ThemePresetPicker(
    theme: ThemeState,
    onPresetChange: (ThemePreset) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Grid(
        modifier = Modifier.fillMaxWidth(),
        config = {
            val width = constraints.maxWidth.toDp()
            val gapSize = SyncClipSpacing.sm
            val count = ThemePreset.entries.size
            val columns = ((width + gapSize) / (PresetCellMinWidth + gapSize))
                .toInt()
                .coerceIn(1, count)
            val rows = (count + columns - 1) / columns
            val cellWidth = ((width - gapSize * (columns - 1)) / columns).coerceAtLeast(0.dp)
            repeat(columns) { column(cellWidth) }
            repeat(rows) { row(PresetCellHeight) }
            gap(gapSize)
        },
    ) {
        ThemePreset.entries.forEach { entry ->
            val selected = entry == theme.preset
            val colors = when (entry) {
                ThemePreset.DYNAMIC -> listOf(scheme.primary, scheme.secondary, scheme.tertiary)
                ThemePreset.RANDOM -> RandomTheme.swatch(theme.randomSeed ?: DEFAULT_SEED)
                else -> entry.swatchColors
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onPresetChange(entry) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(colors))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) scheme.primary else scheme.outlineVariant,
                                shape = CircleShape,
                            ),
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(scheme.surface)
                                .border(1.dp, scheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            SyncClipIcon(
                                kind = SyncClipIconKind.CHECK,
                                contentDescription = null,
                                tint = scheme.primary,
                                size = 10.dp,
                            )
                        }
                    }
                }
                Text(
                    text = ThemePresetLabel(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    // Две строки: «Динамическая» не влезает в узкую плитку.
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Минимальная ширина плитки палитры: кружок 34 dp + подпись целиком. */
private val PresetCellMinWidth = 88.dp

/** Высота плитки: кружок, до двух строк подписи и вертикальные отступы. */
private val PresetCellHeight = 76.dp

@Composable
private fun ThemePresetLabel(preset: ThemePreset): String = when (preset) {
    ThemePreset.AQUA -> stringResource(Res.string.settings_palette_aqua)
    ThemePreset.WINTER -> stringResource(Res.string.settings_palette_winter)
    ThemePreset.MONOCHROME -> stringResource(Res.string.settings_palette_monochrome)
    ThemePreset.DYNAMIC -> stringResource(Res.string.settings_palette_dynamic)
    ThemePreset.RANDOM -> stringResource(Res.string.settings_palette_random)
}

@Composable
private fun FontSizeSelector(
    selected: FontScale,
    onSelect: (FontScale) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FontScale.entries.forEach { entry ->
            val isSelected = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) scheme.surface else Color.Transparent)
                    .clickable { onSelect(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = FontScaleLabel(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FontScaleLabel(scale: FontScale): String = when (scale) {
    FontScale.SMALL -> stringResource(Res.string.settings_font_small)
    FontScale.MEDIUM -> stringResource(Res.string.settings_font_medium)
    FontScale.LARGE -> stringResource(Res.string.settings_font_large)
    FontScale.EXTRA_LARGE -> stringResource(Res.string.settings_font_extra_large)
}

/** Палитра до первого выбора «Random» — та же, что в теме по умолчанию. */
private const val DEFAULT_SEED = 0x2F7FD5L

@Preview(widthDp = 420)
@Composable
private fun SettingsAppearanceSectionPreview() {
    SyncClipTheme {
        Column {
            SettingsAppearanceSection(
                theme = ThemeState(preset = ThemePreset.RANDOM, randomSeed = 0x123456L),
                fontScale = FontScale.MEDIUM,
                onThemeChange = {},
                onRegenerateRandomTheme = {},
                onFontScaleChange = {},
                modifier = Modifier.width(392.dp),
            )
        }
    }
}
