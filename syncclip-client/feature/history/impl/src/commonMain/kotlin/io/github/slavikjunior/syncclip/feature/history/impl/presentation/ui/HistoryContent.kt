package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.ClipboardCard
import io.github.slavikjunior.syncclip.core.designsystem.component.EmptyState
import io.github.slavikjunior.syncclip.core.designsystem.component.SearchField
import io.github.slavikjunior.syncclip.core.designsystem.component.SectionHeader
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.theme.LocalSyncClipColors
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryEntryUi
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryFilter
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryIntent
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryUiState
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_count
import syncclip.feature.history.impl.generated.resources.history_delete
import syncclip.feature.history.impl.generated.resources.history_empty
import syncclip.feature.history.impl.generated.resources.history_empty_title
import syncclip.feature.history.impl.generated.resources.history_favorites_empty
import syncclip.feature.history.impl.generated.resources.history_favorites_empty_title
import syncclip.feature.history.impl.generated.resources.history_favorite
import syncclip.feature.history.impl.generated.resources.history_found
import syncclip.feature.history.impl.generated.resources.history_load_more
import syncclip.feature.history.impl.generated.resources.history_pin
import syncclip.feature.history.impl.generated.resources.history_pin_swipe
import syncclip.feature.history.impl.generated.resources.history_recent
import syncclip.feature.history.impl.generated.resources.history_send
import syncclip.feature.history.impl.generated.resources.history_unfavorite
import syncclip.feature.history.impl.generated.resources.history_search_clear
import syncclip.feature.history.impl.generated.resources.history_search_empty
import syncclip.feature.history.impl.generated.resources.history_search_empty_title
import syncclip.feature.history.impl.generated.resources.history_search_placeholder
import syncclip.feature.history.impl.generated.resources.history_search_shortcut
import syncclip.feature.history.impl.generated.resources.history_select_entry
import syncclip.feature.history.impl.generated.resources.history_select_entry_hint
import syncclip.feature.history.impl.generated.resources.history_subtitle
import syncclip.feature.history.impl.generated.resources.history_title
import syncclip.feature.history.impl.generated.resources.history_unpin
import syncclip.feature.history.impl.generated.resources.history_unpin_swipe
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onIntent: (HistoryIntent) -> Unit,
    useTwoPane: Boolean,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
) {
    LaunchedEffect(useTwoPane, state.entries, state.selectedEntryId) {
        if (useTwoPane && state.entries.none { entry -> entry.id == state.selectedEntryId }) {
            state.entries.firstOrNull()?.let { entry ->
                onIntent(HistoryIntent.Select(entry.id, navigate = false))
            }
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = {
            // US-016: отправка текущего буфера на выбранные устройства.
            FloatingActionButton(
                onClick = { onIntent(HistoryIntent.OpenSendTargets) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                SyncClipIcon(
                    kind = SyncClipIconKind.SEND,
                    contentDescription = stringResource(Res.string.history_send),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 20.dp,
                )
            }
        },
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (useTwoPane) {
                HistoryListPane(
                    modifier = Modifier.weight(0.42f),
                    state = state,
                    onIntent = onIntent,
                    showShortcut = true,
                    twoPane = true,
                    nowMillis = nowMillis,
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val selected = state.entries.firstOrNull { entry ->
                    entry.id == state.selectedEntryId
                }
                if (selected != null) {
                    HistoryEntryPaneContent(
                        modifier = Modifier.weight(0.58f).statusBarsPadding(),
                        entry = selected,
                        nowMillis = nowMillis,
                        onPinClick = { onIntent(HistoryIntent.TogglePin(selected.id)) },
                        onDeleteClick = if (selected.isPinned) {
                            null
                        } else {
                            { onIntent(HistoryIntent.Delete(selected.id)) }
                        },
                    )
                } else {
                    Box(
                        modifier = Modifier.weight(0.58f).fillMaxHeight().statusBarsPadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(Res.string.history_select_entry),
                            message = stringResource(Res.string.history_select_entry_hint),
                            icon = SyncClipIconKind.HISTORY,
                        )
                    }
                }
            } else {
                HistoryListPane(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onIntent = onIntent,
                    showShortcut = false,
                    twoPane = false,
                    nowMillis = nowMillis,
                )
            }
        }
    }
    if (state.sendDialog != null) {
        HistorySendDialog(state = state.sendDialog, onIntent = onIntent)
    }
}

@Composable
private fun HistoryListPane(
    modifier: Modifier,
    state: HistoryUiState,
    onIntent: (HistoryIntent) -> Unit,
    showShortcut: Boolean,
    twoPane: Boolean,
    nowMillis: Long,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = SyncClipSpacing.screen),
    ) {
        Spacer(modifier = Modifier.height(SyncClipSpacing.xs))
        HistoryHeader()
        Spacer(modifier = Modifier.height(SyncClipSpacing.md))
        SearchField(
            value = state.query,
            onValueChange = { query -> onIntent(HistoryIntent.QueryChanged(query)) },
            placeholder = stringResource(Res.string.history_search_placeholder),
            onClear = { onIntent(HistoryIntent.ClearQuery) },
            clearContentDescription = stringResource(Res.string.history_search_clear),
            shortcutHint = if (showShortcut) {
                stringResource(Res.string.history_search_shortcut)
            } else {
                null
            },
        )
        Spacer(modifier = Modifier.height(SyncClipSpacing.md))
        HistoryFilterChips(
            selected = state.filter,
            onSelect = { filter -> onIntent(HistoryIntent.SetFilter(filter)) },
        )
        Spacer(modifier = Modifier.height(SyncClipSpacing.lg))
        val trailing = if (state.filter == HistoryFilter.ALL) {
            stringResource(Res.string.history_count, state.entries.size, state.totalCount)
        } else {
            stringResource(Res.string.history_found, state.entries.size)
        }
        SectionHeader(
            title = stringResource(Res.string.history_recent),
            trailing = trailing,
        )
        Spacer(modifier = Modifier.height(SyncClipSpacing.sm))
        HistoryList(
            modifier = Modifier.weight(1f),
            state = state,
            onIntent = onIntent,
            twoPane = twoPane,
            nowMillis = nowMillis,
        )
    }
}

@Composable
private fun HistoryHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.history_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SyncBadge()
    }
}

@Composable
private fun SyncBadge() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SyncClipIcon(
                kind = SyncClipIconKind.SYNC,
                contentDescription = null,
                tint = scheme.primary,
                size = 20.dp,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LocalSyncClipColors.current.online),
            )
        }
    }
}

@Composable
private fun HistoryFilterChips(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HistoryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = HistoryFilterLabel(filter),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = when (filter) {
                    HistoryFilter.PINNED -> {
                        {
                            SyncClipIcon(
                                kind = SyncClipIconKind.PIN,
                                contentDescription = null,
                                size = 13.dp,
                            )
                        }
                    }

                    HistoryFilter.FAVORITES -> {
                        {
                            SyncClipIcon(
                                kind = SyncClipIconKind.STAR,
                                contentDescription = null,
                                size = 13.dp,
                            )
                        }
                    }

                    else -> null
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = scheme.surface,
                    labelColor = scheme.onSurfaceVariant,
                    selectedContainerColor = scheme.primaryContainer,
                    selectedLabelColor = scheme.onPrimaryContainer,
                    selectedLeadingIconColor = scheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun HistoryList(
    modifier: Modifier,
    state: HistoryUiState,
    onIntent: (HistoryIntent) -> Unit,
    twoPane: Boolean,
    nowMillis: Long,
) {
    when {
        state.isLoading && state.entries.isEmpty() -> {
            HistorySkeletonList(modifier = modifier)
        }

        state.entries.isEmpty() -> {
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val isSearch = state.query.isNotBlank()
                val isFavorites = state.filter == HistoryFilter.FAVORITES
                EmptyState(
                    title = stringResource(
                        when {
                            isSearch -> Res.string.history_search_empty_title
                            isFavorites -> Res.string.history_favorites_empty_title
                            else -> Res.string.history_empty_title
                        },
                    ),
                    message = stringResource(
                        when {
                            isSearch -> Res.string.history_search_empty
                            isFavorites -> Res.string.history_favorites_empty
                            else -> Res.string.history_empty
                        },
                    ),
                    icon = when {
                        isSearch -> SyncClipIconKind.SEARCH
                        isFavorites -> SyncClipIconKind.STAR
                        else -> SyncClipIconKind.HISTORY
                    },
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = SyncClipSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
            ) {
                items(
                    items = state.entries,
                    key = { entry -> entry.id },
                    contentType = { "history_entry" },
                ) { entry ->
                    HistoryEntryRow(
                        entry = entry,
                        query = state.query,
                        isSelected = twoPane && entry.id == state.selectedEntryId,
                        navigateOnSelect = !twoPane,
                        nowMillis = nowMillis,
                        onIntent = onIntent,
                    )
                }
                if (state.isLoadingMore) {
                    item(key = "loading_more", contentType = "progress") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(SyncClipSpacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (state.hasMore) {
                    item(key = "load_more", contentType = "action") {
                        TextButton(
                            onClick = { onIntent(HistoryIntent.LoadNextPage) },
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(Res.string.history_load_more))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryEntryRow(
    entry: HistoryEntryUi,
    query: String,
    isSelected: Boolean,
    navigateOnSelect: Boolean,
    nowMillis: Long,
    onIntent: (HistoryIntent) -> Unit,
) {
    val deleteLabel = stringResource(Res.string.history_delete)
    val pinLabel = stringResource(
        if (entry.isPinned) Res.string.history_unpin_swipe else Res.string.history_pin_swipe,
    )
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when {
                // US-019: полный свайп вправо — закрепление. Возврат `false`
                // оставляет карточку в списке: закрепление не удаляет запись.
                value == SwipeToDismissBoxValue.StartToEnd -> {
                    onIntent(HistoryIntent.TogglePin(entry.id))
                    false
                }

                value == SwipeToDismissBoxValue.EndToStart && !entry.isPinned -> {
                    onIntent(HistoryIntent.Delete(entry.id))
                    true
                }

                else -> false
            }
        },
    )
    val swipingToPin = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = !entry.isPinned,
        backgroundContent = {
            val background = if (swipingToPin) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
            val content = if (swipingToPin) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(background)
                    .padding(SyncClipSpacing.lg),
                contentAlignment = if (swipingToPin) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
                ) {
                    if (swipingToPin) {
                        SyncClipIcon(
                            kind = SyncClipIconKind.PIN,
                            contentDescription = null,
                            tint = content,
                            size = 16.dp,
                        )
                    }
                    Text(
                        text = if (swipingToPin) pinLabel else deleteLabel,
                        color = content,
                    )
                }
            }
        },
        content = {
            ClipboardCard(
                text = HighlightText(
                    text = entry.text,
                    query = query,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ),
                type = entry.type.toClipboardCardType(),
                typeLabel = HistoryTypeLabel(entry.type),
                sourceLabel = HistorySourceLabel(entry),
                sourceIcon = entry.sourceIcon.toSyncClipIcon(),
                timeLabel = HistoryTimeLabel(entry.timestampMillis, nowMillis),
                badgeLabel = HistoryExpiryLabel(entry.expiresAtMillis, nowMillis),
                isPinned = entry.isPinned,
                isFavorite = entry.isFavorite,
                isSelected = isSelected,
                onClick = {
                    onIntent(HistoryIntent.Select(entry.id, navigate = navigateOnSelect))
                },
                // US-020: двойной тап копирует текст в системный буфер.
                onDoubleClick = { onIntent(HistoryIntent.CopyToClipboard(entry.id)) },
                onPinClick = { onIntent(HistoryIntent.TogglePin(entry.id)) },
                pinContentDescription = stringResource(
                    if (entry.isPinned) {
                        Res.string.history_unpin
                    } else {
                        Res.string.history_pin
                    },
                ),
                // US-017: звезда избранного.
                onFavoriteClick = { onIntent(HistoryIntent.ToggleFavorite(entry.id)) },
                favoriteContentDescription = stringResource(
                    if (entry.isFavorite) {
                        Res.string.history_unfavorite
                    } else {
                        Res.string.history_favorite
                    },
                ),
            )
        },
    )
}

@Composable
private fun HistorySkeletonList(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
    ) {
        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(SyncClipSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun HistoryContentPreview() {
    val now = Clock.System.now().toEpochMilliseconds()
    SyncClipTheme {
        HistoryContent(
            state = HistoryUiState(
                entries = listOf(
                    HistoryEntryUi(
                        id = "1",
                        text = "github.com/JetBrains/compose-multiplatform",
                        timestampMillis = now - 2 * 60_000L,
                        sourceDeviceId = "mac",
                        isPinned = true,
                        sourceDeviceName = "MacBook Pro",
                        sourceIcon = DeviceIcon.LAPTOP,
                        type = HistoryEntryType.LINK,
                    ),
                    HistoryEntryUi(
                        id = "2",
                        text = "Check Room migrations on iOS before the release",
                        timestampMillis = now - 12 * 60_000L,
                        sourceDeviceId = "phone",
                        isPinned = false,
                        sourceDeviceName = "iPhone 15 Pro",
                        sourceIcon = DeviceIcon.PHONE,
                        type = HistoryEntryType.TEXT,
                    ),
                ),
                totalCount = 10,
            ),
            onIntent = {},
            useTwoPane = false,
        )
    }
}
