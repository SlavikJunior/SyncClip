package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import syncclip.core.designsystem.generated.resources.Res
import syncclip.core.designsystem.generated.resources.inter_variable
import syncclip.core.designsystem.generated.resources.jetbrains_mono_variable

/**
 * Типографика SyncClip (docs/UI_DESIGN.md §2.2).
 *
 * UI — Inter, содержимое буфера и код — JetBrains Mono. Оба шрифта
 * вариативные, лежат в `composeResources/font` с лицензиями OFL.
 */
@Composable
internal fun SyncClipTypography(): Typography {
    val ui = SyncClipUiFontFamily()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            lineHeight = 56.sp,
            letterSpacing = (-0.5).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 48.sp,
            letterSpacing = (-0.5).sp,
        ),
        displaySmall = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.25).sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.25).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.1.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = ui,
            fontWeight = FontWeight.Medium,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}

/**
 * Моноширинный шрифт для сниппетов и кода.
 *
 * Вызывать в composition: [syncClipTypography] уже использует Inter, а этот
 * FontFamily нужен экранам, которые показывают содержимое буфера.
 */
@Composable
public fun SyncClipMonoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_variable, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_variable, FontWeight.Medium),
)

@Composable
private fun SyncClipUiFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_variable, FontWeight.Normal),
    Font(Res.font.inter_variable, FontWeight.Medium),
    Font(Res.font.inter_variable, FontWeight.SemiBold),
    Font(Res.font.inter_variable, FontWeight.Bold),
)
