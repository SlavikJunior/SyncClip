package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipMonoFontFamily

/**
 * Карточка записи буфера: текст, ссылка или код.
 *
 * Все подписи передаются снаружи, чтобы компонент не знал о локализации.
 * [onPinClick] == null рисует статичную иконку закрепления без действия.
 * Звёздочка избранного (US-017) и двойной тап для копирования (US-020) —
 * такие же необязательные действия, как закрепление.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun ClipboardCard(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    type: ClipboardCardType = ClipboardCardType.TEXT,
    typeLabel: String? = null,
    sourceLabel: String? = null,
    sourceIcon: SyncClipIconKind = SyncClipIconKind.LAPTOP,
    timeLabel: String? = null,
    badgeLabel: String? = null,
    isPinned: Boolean = false,
    isFavorite: Boolean = false,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onPinClick: (() -> Unit)? = null,
    pinContentDescription: String? = null,
    onFavoriteClick: (() -> Unit)? = null,
    favoriteContentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val cardColor = when {
        isPinned -> scheme.surfaceContainerHigh
        isFavorite -> scheme.surfaceContainer
        else -> scheme.surface
    }
    val borderColor = when {
        isSelected -> scheme.primary
        isPinned -> scheme.primary.copy(alpha = 0.35f)
        isFavorite -> scheme.tertiary.copy(alpha = 0.35f)
        else -> scheme.outlineVariant
    }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                when {
                    onClick == null -> Modifier
                    onDoubleClick == null -> Modifier.clickable(
                        interactionSource = interaction,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick,
                    )

                    else -> Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick,
                        onDoubleClick = onDoubleClick,
                    )
                },
            ),
        shape = shape,
        color = cardColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
    ) {

        Column(
            modifier = Modifier.padding(
                horizontal = SyncClipSpacing.lg,
                vertical = SyncClipSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
        ) {
            if (typeLabel != null || isPinned || onPinClick != null || onFavoriteClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = typeLabel.orEmpty().uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFavorite || onFavoriteClick != null) {
                        CardAction(
                            kind = if (isFavorite) {
                                SyncClipIconKind.STAR_FILLED
                            } else {
                                SyncClipIconKind.STAR
                            },
                            tint = if (isFavorite) {
                                scheme.tertiary
                            } else {
                                scheme.onSurfaceVariant
                            },
                            contentDescription = favoriteContentDescription,
                            onClick = onFavoriteClick,
                        )
                    }
                    if (isPinned || onPinClick != null) {
                        PinAction(
                            pinned = isPinned,
                            contentDescription = pinContentDescription,
                            onClick = onPinClick,
                        )
                    }
                }
            }

            when (type) {
                ClipboardCardType.LINK -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
                    ) {
                        SyncClipIcon(
                            kind = SyncClipIconKind.LINK,
                            contentDescription = null,
                            tint = scheme.primary,
                            size = 16.dp,
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SyncClipMonoFontFamily(),
                            ),
                            color = scheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                ClipboardCardType.CODE -> {
                    Surface(
                        color = scheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SyncClipMonoFontFamily(),
                                lineHeight = 19.sp,
                            ),
                            color = scheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = SyncClipSpacing.md,
                                vertical = SyncClipSpacing.sm,
                            ),
                        )
                    }
                }

                ClipboardCardType.TEXT -> {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (sourceLabel != null || timeLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (sourceLabel != null) {
                        SyncClipIcon(
                            kind = sourceIcon,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            size = 14.dp,
                        )
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (sourceLabel != null && timeLabel != null) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(scheme.outline),
                        )
                    }
                    if (timeLabel != null) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (badgeLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SyncClipIcon(
                        kind = SyncClipIconKind.LOCK,
                        contentDescription = null,
                        tint = scheme.error,
                        size = 12.dp,
                    )
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.error,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinAction(
    pinned: Boolean,
    contentDescription: String?,
    onClick: (() -> Unit)?,
) {
    CardAction(
        kind = SyncClipIconKind.PIN,
        tint = if (pinned) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        contentDescription = contentDescription,
        onClick = onClick,
    )
}

/** Иконка-действие в шапке карточки: закрепление, избранное. */
@Composable
private fun CardAction(
    kind: SyncClipIconKind,
    tint: androidx.compose.ui.graphics.Color,
    contentDescription: String?,
    onClick: (() -> Unit)?,
) {
    val modifier = Modifier
        .size(28.dp)
        .clip(CircleShape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        SyncClipIcon(
            kind = kind,
            contentDescription = contentDescription,
            tint = tint,
            size = 16.dp,
        )
    }
}

@Preview
@Composable
private fun ClipboardCardPreview() {
    SyncClipTheme {
        Column(
            modifier = Modifier.padding(SyncClipSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.md),
        ) {
            ClipboardCard(
                text = AnnotatedString("github.com/JetBrains/compose-multiplatform"),
                type = ClipboardCardType.LINK,
                typeLabel = "LINK",
                sourceLabel = "MacBook Pro",
                sourceIcon = SyncClipIconKind.LAPTOP,
                timeLabel = "2 min ago",
                isPinned = true,
                onPinClick = {},
                pinContentDescription = "Unpin",
            )
            ClipboardCard(
                text = AnnotatedString("Check Room migrations on iOS before the release"),
                sourceLabel = "iPhone 15 Pro",
                sourceIcon = SyncClipIconKind.PHONE,
                timeLabel = "12 min ago",
            )
            ClipboardCard(
                text = AnnotatedString("val state = _state.asStateFlow()"),
                type = ClipboardCardType.CODE,
                typeLabel = "CODE · KOTLIN",
                sourceLabel = "iMac",
                timeLabel = "1 h ago",
            )
        }
    }
}
