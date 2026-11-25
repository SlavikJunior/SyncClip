package io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceNameError

/** Валидация имени по контракту `PATCH /devices/{id}` (US-001). */
internal class ValidateDeviceNameUseCase {

    operator fun invoke(name: String): Result<String> = when {
        name.isEmpty() -> Result.failure(DeviceNameError.Empty)
        name.length > MAX_LENGTH -> Result.failure(DeviceNameError.TooLong)
        !NAME_PATTERN.matches(name) -> Result.failure(DeviceNameError.InvalidFormat)
        else -> Result.success(name)
    }

    private companion object {
        const val MAX_LENGTH = 50
        val NAME_PATTERN = Regex("^[a-zA-Z0-9 -]{1,50}$")
    }
}
