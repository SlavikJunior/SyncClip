package io.github.slavikjunior.syncclip.server.contracts.validation

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Разбор IP-литералов и CIDR без DNS: `InetAddress.getByName` вызывается только
 * после синтаксической проверки, поэтому hostname в поле IP не превращается
 * в сетевой запрос (SEC-11).
 */
object IpAddresses {

    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val IPV6 = Regex("^[0-9a-fA-F:.]+$")

    /** `null` — не литерал или невозможный адрес (`999.999.999.999`, `::::`). */
    fun parseLiteral(raw: String?): InetAddress? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = when {
            value.contains(':') -> {
                if (!IPV6.matches(value) || value.count { it == ':' } < 2) return null
                runCatching { InetAddress.getByName(value) }.getOrNull()
            }

            IPV4.matches(value) -> {
                if (value.split('.').any { it.toIntOrNull() !in 0..255 }) return null
                runCatching { InetAddress.getByName(value) }.getOrNull()
            }

            else -> null
        } ?: return null
        // Смешанные формы (`::ffff:1.2.3.4`) нормализуем к исходному семейству.
        return when {
            value.contains(':') && parsed is Inet6Address -> parsed
            !value.contains(':') && parsed is Inet4Address -> parsed
            else -> null
        }
    }

    /** Каноническая форма для колонки `inet`; `null`, если это не литерал. */
    fun canonical(raw: String?): String? = parseLiteral(raw)?.hostAddress

    /** CIDR (`10.0.0.0/8`) или одиночный адрес (`127.0.0.1` → /32, /128). */
    fun parseCidr(raw: String?): IpRange? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val addressPart = value.substringBefore('/')
        val address = parseLiteral(addressPart) ?: return null
        val maxPrefix = address.address.size * 8
        val prefix = if ('/' in value) {
            value.substringAfter('/').toIntOrNull() ?: return null
        } else {
            maxPrefix
        }
        if (prefix !in 0..maxPrefix) return null
        return IpRange(address, prefix)
    }

    data class IpRange(val network: InetAddress, val prefixLength: Int) {

        fun contains(address: InetAddress): Boolean {
            val candidate = address.address
            val base = network.address
            if (candidate.size != base.size) return false
            val fullBytes = prefixLength / 8
            val remainderBits = prefixLength % 8
            for (index in 0 until fullBytes) {
                if (candidate[index] != base[index]) return false
            }
            if (remainderBits == 0) return true
            val mask = (0xFF shl (8 - remainderBits)) and 0xFF
            return (candidate[fullBytes].toInt() and mask) == (base[fullBytes].toInt() and mask)
        }
    }
}
