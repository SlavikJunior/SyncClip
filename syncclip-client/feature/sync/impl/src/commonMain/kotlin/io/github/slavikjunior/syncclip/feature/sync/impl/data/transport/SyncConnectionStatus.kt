package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

/** Состояние соединения с relay-сервером (FR-2.3). */
internal enum class SyncConnectionStatus {

    /** Мастер-пароль не введён — шифровать нечем. */
    LOCKED,

    /** Соединения нет, идёт переподключение. */
    OFFLINE,

    /** WebSocket открыт. */
    ONLINE,

    /** Пять неудачных попыток подряд: показываем пользователю предупреждение. */
    SUSPENDED,
}
