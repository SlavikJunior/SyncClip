package io.github.slavikjunior.syncclip.shared.model

/**
 * Иконка устройства из контракта `PATCH /devices/{deviceId}` (US-002).
 */
enum class DeviceIcon {
    PHONE,
    TABLET,
    LAPTOP,
    DESKTOP,
    WATCH,
    TV,
    ;

    companion object {
        /**
         * Классификация по OC и признакам устройства. Чистая функция — можно
         * проверять юнит-тестами без платформенных API.
         */
        fun resolve(platform: DevicePlatform, isTablet: Boolean, hasBattery: Boolean): DeviceIcon =
            when (platform) {
                DevicePlatform.ANDROID -> if (isTablet) TABLET else PHONE
                DevicePlatform.IOS -> if (isTablet) TABLET else PHONE
                DevicePlatform.MACOS -> if (hasBattery) LAPTOP else DESKTOP
                DevicePlatform.WINDOWS,
                DevicePlatform.LINUX,
                -> if (hasBattery) LAPTOP else DESKTOP
                DevicePlatform.UNKNOWN -> DESKTOP
            }
    }
}
