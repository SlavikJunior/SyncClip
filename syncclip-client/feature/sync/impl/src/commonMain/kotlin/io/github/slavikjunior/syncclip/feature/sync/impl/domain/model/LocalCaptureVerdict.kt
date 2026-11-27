package io.github.slavikjunior.syncclip.feature.sync.impl.domain.model

/** Результат проверки локального копирования перед сохранением. */
internal enum class LocalCaptureVerdict {

    /** Текст можно сохранять и отправлять. */
    ACCEPT,

    /** Пустой или пробельный текст не синхронизируется. */
    EMPTY,

    /** Текст больше лимита паспорта (512 КиБ UTF-8). */
    TOO_LARGE,
}
