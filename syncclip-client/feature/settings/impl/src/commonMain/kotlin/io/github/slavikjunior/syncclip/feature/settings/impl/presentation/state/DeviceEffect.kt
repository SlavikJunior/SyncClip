package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import org.jetbrains.compose.resources.StringResource

internal sealed interface DeviceEffect {

    /**
     * Сообщение в snackbar. [args] — аргументы форматирования строки:
     * экспорт и импорт возвращают счётчики, а не фиксированный текст.
     */
    data class ShowMessage(
        val message: StringResource,
        val args: List<Any> = emptyList(),
    ) : DeviceEffect
}
