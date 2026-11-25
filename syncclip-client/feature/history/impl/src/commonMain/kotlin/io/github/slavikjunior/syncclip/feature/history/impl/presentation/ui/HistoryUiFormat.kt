package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.slavikjunior.syncclip.core.designsystem.component.ClipboardCardType
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryEntryUi
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryFilter
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_filter_all
import syncclip.feature.history.impl.generated.resources.history_filter_code
import syncclip.feature.history.impl.generated.resources.history_filter_links
import syncclip.feature.history.impl.generated.resources.history_filter_pinned
import syncclip.feature.history.impl.generated.resources.history_source_current
import syncclip.feature.history.impl.generated.resources.history_source_unknown
import syncclip.feature.history.impl.generated.resources.history_time_days
import syncclip.feature.history.impl.generated.resources.history_time_hours
import syncclip.feature.history.impl.generated.resources.history_time_minutes
import syncclip.feature.history.impl.generated.resources.history_time_now
import syncclip.feature.history.impl.generated.resources.history_type_code
import syncclip.feature.history.impl.generated.resources.history_type_link

internal fun DeviceIcon?.toSyncClipIcon(): SyncClipIconKind = when (this) {
    DeviceIcon.PHONE -> SyncClipIconKind.PHONE
    DeviceIcon.TABLET -> SyncClipIconKind.TABLET
    DeviceIcon.LAPTOP -> SyncClipIconKind.LAPTOP
    DeviceIcon.DESKTOP -> SyncClipIconKind.DESKTOP
    DeviceIcon.WATCH -> SyncClipIconKind.WATCH
    DeviceIcon.TV -> SyncClipIconKind.TV
    null -> SyncClipIconKind.DEVICES
}

internal fun HistoryEntryType.toClipboardCardType(): ClipboardCardType = when (this) {
    HistoryEntryType.TEXT -> ClipboardCardType.TEXT
    HistoryEntryType.LINK -> ClipboardCardType.LINK
    HistoryEntryType.CODE -> ClipboardCardType.CODE
}

@Composable
internal fun HistoryTypeLabel(type: HistoryEntryType): String? = when (type) {
    HistoryEntryType.TEXT -> null
    HistoryEntryType.LINK -> stringResource(Res.string.history_type_link)
    HistoryEntryType.CODE -> stringResource(Res.string.history_type_code)
}

@Composable
internal fun HistoryFilterLabel(filter: HistoryFilter): String = when (filter) {
    HistoryFilter.ALL -> stringResource(Res.string.history_filter_all)
    HistoryFilter.PINNED -> stringResource(Res.string.history_filter_pinned)
    HistoryFilter.LINKS -> stringResource(Res.string.history_filter_links)
    HistoryFilter.CODE -> stringResource(Res.string.history_filter_code)
}

@Composable
internal fun HistorySourceLabel(entry: HistoryEntryUi): String = entry.sourceDeviceName
    ?: if (entry.sourceDeviceId == null) {
        stringResource(Res.string.history_source_current)
    } else {
        stringResource(Res.string.history_source_unknown)
    }

@Composable
internal fun HistoryTimeLabel(timestampMillis: Long, nowMillis: Long): String {
    val diff = (nowMillis - timestampMillis).coerceAtLeast(0L)
    return when {
        diff < MINUTE_MILLIS -> stringResource(Res.string.history_time_now)
        diff < HOUR_MILLIS -> stringResource(
            Res.string.history_time_minutes,
            (diff / MINUTE_MILLIS).toInt(),
        )
        diff < DAY_MILLIS -> stringResource(
            Res.string.history_time_hours,
            (diff / HOUR_MILLIS).toInt(),
        )
        else -> stringResource(
            Res.string.history_time_days,
            (diff / DAY_MILLIS).toInt(),
        )
    }
}

internal fun HighlightText(
    text: String,
    query: String,
    color: Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var index = 0
        while (index <= text.length) {
            val start = text.indexOf(query, index, ignoreCase = true)
            if (start < 0) break
            append(text.substring(index, start))
            val end = start + query.length
            pushStyle(SpanStyle(background = color))
            append(text.substring(start, end))
            pop()
            index = end
        }
        append(text.substring(index))
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
private const val DAY_MILLIS = 24L * HOUR_MILLIS
