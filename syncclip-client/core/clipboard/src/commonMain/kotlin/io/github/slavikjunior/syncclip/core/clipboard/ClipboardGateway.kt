package io.github.slavikjunior.syncclip.core.clipboard

import kotlinx.coroutines.flow.Flow

/**
 * Системный буфер с правилами паспорта: защита от эха (FR-2.7) и от повторной
 * обработки одного и того же текста (FR-2.5).
 *
 * Единственный вход для фич: платформенный [SystemClipboard] им не виден.
 */
public interface ClipboardGateway {

    /**
     * Пользовательские копирования.
     *
     * Поток холодный: его должен собирать ровно один владелец приложения
     * (конвейер захвата), иначе дедупликация и защита от эха будут считаться
     * отдельно для каждого коллектора.
     */
    public fun localCopies(): Flow<ClipboardCapture>

    /** Текущий текст буфера — для ручной отправки и отладки. */
    public suspend fun currentText(): String?

    /**
     * Программная вставка удалённого текста.
     *
     * Следующее системное событие с этим же текстом не считается локальным
     * копированием и не уходит обратно в сеть.
     */
    public suspend fun insert(text: String)

    /** Время последнего принятого локального копирования; `0`, если их не было. */
    public fun lastLocalCaptureMillis(): Long
}
