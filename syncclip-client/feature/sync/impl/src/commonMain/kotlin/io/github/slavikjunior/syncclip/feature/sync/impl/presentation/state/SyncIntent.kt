package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

internal sealed interface SyncIntent {

    /** Остановить или возобновить отправку и приём (FR-2.9). */
    data object ToggleOnline : SyncIntent

    /** Переключить вставку удалённых пакетов в системный буфер (FR-2.10). */
    data object ToggleAutoInsert : SyncIntent

    /** Захватить текущее содержимое буфера вручную (Share Sheet, iOS-кнопка). */
    data object CaptureCurrent : SyncIntent

    /** Первая отрисовка экрана: подгрузить каталог устройств. */
    data object StartListening : SyncIntent
}
