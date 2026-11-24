package io.github.slavikjunior.syncclip.server.contracts.util

import java.security.SecureRandom
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * UUIDv7 (RFC 9562) — монотонный по времени идентификатор.
 * Дизайн §10.1: идентификаторы генерирует приложение, БД не полагается
 * на gen_random_uuid(), чтобы порядок вставки был предсказуемым.
 */
object UuidV7 {
    private val random = SecureRandom()

    fun new(): Uuid {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val millis = Instant.now().toEpochMilli()
        bytes[0] = (millis ushr 40).toByte()
        bytes[1] = (millis ushr 32).toByte()
        bytes[2] = (millis ushr 24).toByte()
        bytes[3] = (millis ushr 16).toByte()
        bytes[4] = (millis ushr 8).toByte()
        bytes[5] = millis.toByte()
        // version = 7
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        // variant = RFC 4122 (10xx)
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        return Uuid.fromByteArray(bytes)
    }

    fun newString(): String = new().toString()
}
