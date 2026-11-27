package io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.LocalCaptureVerdict

/**
 * Проверяет локальное копирование перед записью в историю и отправкой.
 *
 * Лимит 512 КиБ задан NFR паспорта и дублируется на клиенте и сервере: клиент
 * обязан заблокировать отправку, а не надеяться на отказ сервера.
 */
internal class AcceptLocalCaptureUseCase(
    private val maxBytes: Int = MAX_CAPTURE_BYTES,
) {

    operator fun invoke(text: String): LocalCaptureVerdict = when {
        text.isBlank() -> LocalCaptureVerdict.EMPTY
        text.encodeToByteArray().size > maxBytes -> LocalCaptureVerdict.TOO_LARGE
        else -> LocalCaptureVerdict.ACCEPT
    }

    private companion object {
        const val MAX_CAPTURE_BYTES = 512 * 1024
    }
}
