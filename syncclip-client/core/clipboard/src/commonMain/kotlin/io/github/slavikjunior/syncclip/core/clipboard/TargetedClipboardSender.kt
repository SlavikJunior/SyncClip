package io.github.slavikjunior.syncclip.core.clipboard

/** Итог адресной отправки текста на выбранные устройства (US-016). */
public sealed interface TargetedSendResult {

    /** Пакет передан транспорту; доставка подтверждается отдельно (ACK). */
    public data object Queued : TargetedSendResult

    /** Нет соединения с сервером: отправлять некуда. */
    public data object Offline : TargetedSendResult

    /** Буфер пуст или текст превышает лимит пакета. */
    public data object Empty : TargetedSendResult
}

/**
 * Отправка текста на конкретные устройства (US-016).
 *
 * Контракт живёт в `core`, а не в фиче синхронизации: экран истории не может
 * зависеть от другой фичи (INV-01). Реализация — в `:feature:sync:impl`.
 *
 * `targetDeviceIds` уходит на сервер как есть: сервер доставляет пакет только
 * этим устройствам. Пустой список означает широковещательную отправку.
 */
public interface TargetedClipboardSender {

    public suspend fun send(text: String, targetDeviceIds: List<String>): TargetedSendResult
}
