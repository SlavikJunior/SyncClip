package io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction

/**
 * Правило вставки удалённого пакета (FR-2.8) и приоритета локального
 * копирования (FR-2.7).
 *
 * Пакет не перезаписывает буфер, если:
 * - авто-вставка выключена — пользователь сам решает, что вставлять;
 * - устройство в Offline — входящие не применяются, но пришедшее сохраняется;
 * - прошло меньше 500 мс с локального копирования — иначе пользователь потеряет
 *   только что скопированный текст.
 */
internal class ResolveIncomingClipboardUseCase(
    private val localCopyWindowMillis: Long = LOCAL_COPY_WINDOW_MILLIS,
) {

    operator fun invoke(
        isOnline: Boolean,
        autoInsert: Boolean,
        nowMillis: Long,
        lastLocalCaptureMillis: Long,
    ): IncomingClipboardAction = when {
        !autoInsert -> IncomingClipboardAction.SAVE_ONLY
        !isOnline -> IncomingClipboardAction.SAVE_ONLY
        nowMillis - lastLocalCaptureMillis < localCopyWindowMillis ->
            IncomingClipboardAction.SAVE_ONLY

        else -> IncomingClipboardAction.SAVE_AND_INSERT
    }

    private companion object {
        const val LOCAL_COPY_WINDOW_MILLIS = 500L
    }
}
