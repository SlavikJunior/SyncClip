package io.github.slavikjunior.syncclip.core.lan

/**
 * Что устройство публикует в mDNS.
 *
 * TXT-записи `userId` и `deviceId` — требование FR-2.1: пиры фильтруются по
 * аккаунту, а собственное устройство исключается по `deviceId`.
 */
public data class LanAdvertisement(
    val deviceId: String,
    val userId: String,
    val port: Int,
)
