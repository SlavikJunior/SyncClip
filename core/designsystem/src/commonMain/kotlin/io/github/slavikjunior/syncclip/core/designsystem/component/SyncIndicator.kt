package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.LocalSyncClipColors
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipMotion
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/** Порог, с которого дуга считается замкнутой (погрешность Float после анимации). */
private const val FULL_CIRCLE_EPSILON = 0.999f

/**
 * Кольцо синхронизации — единственный выразительный акцент приложения.
 *
 * IDLE/SYNCING рисуют градиент primary→tertiary, OFFLINE — пунктирный
 * outline, ERROR — error. Свечение пульсирует только в SYNCING.
 *
 * [progress] задаёт дугу только для SYNCING и ERROR. У IDLE («всё
 * синхронизировано») кольцо всегда замкнуто на 360°, иначе у пользователя
 * остаётся визуальный намёк на незавершённость; у OFFLINE — пустое.
 */
@Composable
public fun SyncIndicator(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    state: SyncIndicatorState = SyncIndicatorState.IDLE,
    progress: Float = 0.72f,
    size: Dp = 168.dp,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val extended = LocalSyncClipColors.current
    val clampedProgress = progress.coerceIn(0f, 1f)
    val targetProgress = when (state) {
        SyncIndicatorState.IDLE -> 1f
        SyncIndicatorState.OFFLINE -> 0f
        SyncIndicatorState.SYNCING, SyncIndicatorState.ERROR -> clampedProgress
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = SyncClipMotion.Slow,
            easing = SyncClipMotion.EmphasizedEasing,
        ),
        label = "sync-progress",
    )
    val pulse: Float = if (state == SyncIndicatorState.SYNCING) {
        val transition = rememberInfiniteTransition(label = "sync-pulse")
        val animated by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = SyncClipMotion.SyncPulse,
                    easing = SyncClipMotion.StandardEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sync-glow",
        )
        animated
    } else {
        1f
    }
    val arcColors = when (state) {
        SyncIndicatorState.ERROR -> listOf(scheme.error, scheme.errorContainer)
        SyncIndicatorState.OFFLINE -> listOf(scheme.outline, scheme.outline)
        else -> listOf(extended.syncStart, extended.syncEnd)
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 7.dp.toPx()
            val radius = (this.size.minDimension - strokeWidth) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val glowRadius = radius + 10.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        arcColors.first().copy(alpha = 0.12f * pulse),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )
            drawCircle(
                color = scheme.surfaceVariant,
                radius = radius,
                center = center,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = if (state == SyncIndicatorState.OFFLINE) {
                        PathEffect.dashPathEffect(floatArrayOf(10f, 14f))
                    } else {
                        null
                    },
                ),
            )
            if (animatedProgress > 0f) {
                // На замкнутом кольце возвращаем стартовый цвет в конец градиента,
                // иначе в точке 0° виден резкий шов.
                val sweepColors = if (animatedProgress >= FULL_CIRCLE_EPSILON) {
                    arcColors + arcColors.first()
                } else {
                    arcColors
                }
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = sweepColors,
                        center = center,
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        pathEffect = if (state == SyncIndicatorState.OFFLINE) {
                            PathEffect.dashPathEffect(floatArrayOf(10f, 14f))
                        } else {
                            null
                        },
                    ),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun SyncIndicatorPreview() {
    SyncClipTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            SyncIndicator(
                title = "All done",
                subtitle = "2 min ago",
                state = SyncIndicatorState.IDLE,
                size = 140.dp,
            )
            SyncIndicator(
                title = "Syncing",
                subtitle = "2 of 3 devices",
                state = SyncIndicatorState.SYNCING,
                size = 140.dp,
            )
            SyncIndicator(
                title = "Offline",
                subtitle = "Paused",
                state = SyncIndicatorState.OFFLINE,
                size = 140.dp,
            )
        }
    }
}
