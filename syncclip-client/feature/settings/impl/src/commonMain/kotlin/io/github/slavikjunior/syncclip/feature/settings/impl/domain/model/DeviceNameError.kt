package io.github.slavikjunior.syncclip.feature.settings.impl.domain.model

/** Типизированные ошибки домена (INV-20). */
internal sealed class DeviceNameError : Error() {

    data object Empty : DeviceNameError()

    data object TooLong : DeviceNameError()

    data object InvalidFormat : DeviceNameError()
}
