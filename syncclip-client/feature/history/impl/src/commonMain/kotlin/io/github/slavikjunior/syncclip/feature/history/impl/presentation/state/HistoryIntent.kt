package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

internal sealed interface HistoryIntent {

    data object Load : HistoryIntent

    data class QueryChanged(val query: String) : HistoryIntent

    data class SetFilter(val filter: HistoryFilter) : HistoryIntent

    data object ClearQuery : HistoryIntent

    data object LoadNextPage : HistoryIntent

    data class Select(val id: String, val navigate: Boolean = true) : HistoryIntent

    data class TogglePin(val id: String) : HistoryIntent

    /** US-017: звезда в карточке. */
    data class ToggleFavorite(val id: String) : HistoryIntent

    /** US-020: двойной тап — копирование в системный буфер. */
    data class CopyToClipboard(val id: String) : HistoryIntent

    data class Delete(val id: String) : HistoryIntent

    data class Restore(val entry: HistoryEntryUi) : HistoryIntent

    data object DismissError : HistoryIntent

    /** US-016: открыть выбор устройств. */
    data object OpenSendTargets : HistoryIntent

    data object DismissSendTargets : HistoryIntent

    data class ToggleSendTarget(val deviceId: String) : HistoryIntent

    /** US-016: отправить текущий буфер выбранным устройствам. */
    data object ConfirmSend : HistoryIntent
}
