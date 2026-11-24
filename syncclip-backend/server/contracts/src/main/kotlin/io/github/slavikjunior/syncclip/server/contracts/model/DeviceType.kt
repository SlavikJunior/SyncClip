package io.github.slavikjunior.syncclip.server.contracts.model

/** Тип устройства: постоянное (учитывается в лимите 3) или гостевое. */
enum class DeviceType(val wire: String) {
    PERMANENT("permanent"),
    GUEST("guest"),
    ;

    companion object {
        fun fromWire(value: String?): DeviceType? = entries.firstOrNull { it.wire == value }
    }
}
