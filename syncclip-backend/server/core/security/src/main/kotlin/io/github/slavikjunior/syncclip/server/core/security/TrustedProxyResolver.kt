package io.github.slavikjunior.syncclip.server.core.security

import io.github.slavikjunior.syncclip.server.contracts.validation.IpAddresses
import java.net.InetAddress

/**
 * Реальный IP клиента (SEC-05).
 *
 * `X-Forwarded-For` — клиентский заголовок, поэтому он учитывается только если
 * TCP-соединение пришло от доверенного reverse proxy. Цепочка читается справа
 * налево: правые адреса добавляли прокси, левые может подделать клиент.
 * Неизвестные/битые значения игнорируются, а не принимаются на веру.
 */
class TrustedProxyResolver(trustedProxies: List<String>) {

    private val ranges: List<IpAddresses.IpRange> = trustedProxies.mapNotNull(IpAddresses::parseCidr)

    fun isTrusted(address: InetAddress?): Boolean =
        address != null && ranges.any { it.contains(address) }

    /**
     * @param remoteAddress адрес сокета (`call.request.local.remoteAddress`).
     * @param forwardedFor сырой `X-Forwarded-For` (может быть null).
     * @return канонический IP клиента или исходный remote-адрес, если это не литерал.
     */
    fun resolve(remoteAddress: String?, forwardedFor: String?): String {
        val remote = IpAddresses.parseLiteral(remoteAddress)
        val fallback = remote?.hostAddress ?: remoteAddress?.trim().orEmpty().ifEmpty { "unknown" }
        if (remote == null || !isTrusted(remote)) return fallback
        val header = forwardedFor?.trim()?.takeIf { it.isNotEmpty() } ?: return fallback

        val hops = header.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        for (hop in hops.asReversed()) {
            val address = IpAddresses.parseLiteral(hop) ?: continue
            if (!isTrusted(address)) return address.hostAddress
        }
        // Вся цепочка — доверенные прокси: берём самый левый валидный адрес.
        return hops.firstNotNullOfOrNull { IpAddresses.parseLiteral(it)?.hostAddress } ?: fallback
    }
}
