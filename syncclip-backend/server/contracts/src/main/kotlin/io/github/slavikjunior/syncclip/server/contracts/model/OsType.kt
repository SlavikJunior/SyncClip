package io.github.slavikjunior.syncclip.server.contracts.model

/** ОС устройства — enum API_SPEC `/auth/register`. */
enum class OsType(val wire: String) {
    ANDROID("Android"),
    IOS("iOS"),
    WINDOWS("Windows"),
    MACOS("macOS"),
    LINUX("Linux"),
    ;

    companion object {
        fun fromWire(value: String?): OsType? = entries.firstOrNull { it.wire == value }
    }
}
