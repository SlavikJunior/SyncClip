package io.github.slavikjunior.syncclip.server.contracts.model

/** Иконка устройства (FR-6.2). */
enum class DeviceIcon(val wire: String) {
    PHONE("PHONE"),
    TABLET("TABLET"),
    LAPTOP("LAPTOP"),
    DESKTOP("DESKTOP"),
    WATCH("WATCH"),
    TV("TV"),
    ;

    companion object {
        fun fromWire(value: String?): DeviceIcon? = entries.firstOrNull { it.wire == value }

        /** CG-19: сервер выводит иконку из OS, клиент правит через PATCH. */
        fun defaultFor(osType: OsType): DeviceIcon = when (osType) {
            OsType.ANDROID, OsType.IOS -> PHONE
            OsType.MACOS -> LAPTOP
            OsType.WINDOWS, OsType.LINUX -> DESKTOP
        }
    }
}
