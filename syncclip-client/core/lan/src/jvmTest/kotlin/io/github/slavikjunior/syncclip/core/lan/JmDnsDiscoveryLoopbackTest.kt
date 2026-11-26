package io.github.slavikjunior.syncclip.core.lan

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.fail

/**
 * Проверка настоящего mDNS на машине разработчика.
 *
 * JmDNS занимает порт 5353 и зависит от сетевого окружения (VPN, firewall,
 * multicast на интерфейсе), поэтому тест включается явно:
 *
 * ```bash
 * SYNCCLIP_MDNS_TEST=1 ./gradlew :core:lan:jvmTest --tests '*JmDnsDiscoveryLoopbackTest*'
 * ```
 */
internal class JmDnsDiscoveryLoopbackTest {

    @Test
    fun `two devices discover each other`() = runBlocking {
        if (System.getenv("SYNCCLIP_MDNS_TEST") != "1") return@runBlocking

        val userId = "user-mdns-test"
        val first = JmDnsLanDiscovery(LanAdvertisement("device-1", userId, 41_001))
        val second = JmDnsLanDiscovery(LanAdvertisement("device-2", userId, 41_002))
        try {
            first.start()
            second.start()

            val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (System.currentTimeMillis() < deadline) {
                val seenByFirst = first.peers.value.any { it.deviceId == "device-2" }
                val seenBySecond = second.peers.value.any { it.deviceId == "device-1" }
                if (seenByFirst && seenBySecond) return@runBlocking
                delay(100)
            }
            fail(
                "mDNS не обнаружил пиров за $TIMEOUT_MILLIS мс: " +
                    "first=${first.peers.value}, second=${second.peers.value}",
            )
        } finally {
            first.stop()
            second.stop()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
