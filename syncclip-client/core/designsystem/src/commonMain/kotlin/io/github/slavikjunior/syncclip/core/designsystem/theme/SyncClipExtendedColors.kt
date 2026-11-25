package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Дополнительные семантические цвета, которых нет в Material 3:
 * успех, предупреждение, статусы онлайн/офлайн.
 *
 * Значения зависят от темы и палитры; читать только через
 * [LocalSyncClipColors], а не литералами в экранах (INV-17).
 */
@Immutable
public data class SyncClipExtendedColors(
    public val success: Color,
    public val warning: Color,
    public val info: Color,
    public val online: Color,
    public val offline: Color,
    public val syncStart: Color,
    public val syncEnd: Color,
)

public val LocalSyncClipColors: androidx.compose.runtime.ProvidableCompositionLocal<SyncClipExtendedColors> =
    staticCompositionLocalOf { AquaLightExtendedColors }

internal val AquaLightExtendedColors: SyncClipExtendedColors = SyncClipExtendedColors(
    success = Color(0xFF0E9F6E),
    warning = Color(0xFFF79009),
    info = Color(0xFF2491C9),
    online = Color(0xFF0E9F6E),
    offline = Color(0xFF7A8F99),
    syncStart = Color(0xFF2491C9),
    syncEnd = Color(0xFF5DB7E3),
)

internal val AquaDarkExtendedColors: SyncClipExtendedColors = SyncClipExtendedColors(
    success = Color(0xFF6FD3A3),
    warning = Color(0xFFFDB022),
    info = Color(0xFF8AB4FF),
    online = Color(0xFF6FD3A3),
    offline = Color(0xFF7E949C),
    syncStart = Color(0xFF5DB7E3),
    syncEnd = Color(0xFFA3C4D4),
)

internal val WinterLightExtendedColors: SyncClipExtendedColors = SyncClipExtendedColors(
    success = Color(0xFF2E7D5B),
    warning = Color(0xFFB26A00),
    info = Color(0xFF3C6169),
    online = Color(0xFF2E7D5B),
    offline = Color(0xFF7C939B),
    syncStart = Color(0xFF5E8B96),
    syncEnd = Color(0xFFA3C4D4),
)

internal val MonoLightExtendedColors: SyncClipExtendedColors = SyncClipExtendedColors(
    success = Color(0xFF4A4A4A),
    warning = Color(0xFF6B6B6B),
    info = Color(0xFF5C5C5C),
    online = Color(0xFF4A4A4A),
    offline = Color(0xFF8A8A8A),
    syncStart = Color(0xFF9E9E9E),
    syncEnd = Color(0xFFE0E0E0),
)

internal val MonoDarkExtendedColors: SyncClipExtendedColors = SyncClipExtendedColors(
    success = Color(0xFFC7C7C7),
    warning = Color(0xFFBDBDBD),
    info = Color(0xFFA3A3A3),
    online = Color(0xFFC7C7C7),
    offline = Color(0xFF8A8A8A),
    syncStart = Color(0xFF9E9E9E),
    syncEnd = Color(0xFFE0E0E0),
)
