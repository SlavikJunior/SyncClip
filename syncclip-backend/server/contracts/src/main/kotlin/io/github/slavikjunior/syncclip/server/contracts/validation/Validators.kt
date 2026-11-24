package io.github.slavikjunior.syncclip.server.contracts.validation

import io.github.slavikjunior.syncclip.server.contracts.error.PayloadTooLargeException
import io.github.slavikjunior.syncclip.server.contracts.error.ValidationException
import io.github.slavikjunior.syncclip.server.contracts.model.DeviceIcon
import io.github.slavikjunior.syncclip.server.contracts.model.DeviceType
import io.github.slavikjunior.syncclip.server.contracts.model.OsType
import java.util.Base64
import java.util.UUID

/**
 * Валидация DTO до вызова сервиса (дизайн §4.5). Все функции бросают
 * [ValidationException] с полем и причиной — тексты в каталоге i18n.
 */
object Validators {

    const val MAX_PLAINTEXT_BYTES: Long = 524_288
    const val MAX_CIPHERTEXT_BYTES: Long = MAX_PLAINTEXT_BYTES + 12 + 16 // IV + authTag
    const val MIN_CIPHERTEXT_BYTES: Long = 28
    const val MAX_PAYLOAD_BASE64_CHARS: Int = 699_088
    const val MIN_CANARY_CHARS: Int = 24
    const val MAX_CANARY_CHARS: Int = 1024
    const val MAX_PASSWORD_LENGTH: Int = 128
    const val MIN_PASSWORD_LENGTH: Int = 8
    const val MAX_DEVICE_NAME_LENGTH: Int = 255
    const val MAX_PATCH_DEVICE_NAME_LENGTH: Int = 50
    /**
     * Реальные токены FCM — 150–200 символов; 2048 оставляет запас и не
     * приближается к лимиту btree-индекса PostgreSQL (~2704 байта) (LOW-09).
     */
    const val MAX_FCM_TOKEN_LENGTH: Int = 2048
    const val MAX_TARGETS: Int = 10
    private const val TIMESTAMP_SKEW_MILLIS: Long = 5 * 60 * 1000

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val HASH_REGEX = Regex("^[0-9a-f]{64}$")
    private val PATCH_NAME_REGEX = Regex("^[A-Za-z0-9 -]{1,50}$")
    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun email(raw: String): String {
        val value = raw.trim().lowercase()
        if (value.length !in 3..255 || !EMAIL_REGEX.matches(value)) {
            throw ValidationException("email", "invalid_email")
        }
        return value
    }

    fun password(raw: String): String {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (raw.length < MIN_PASSWORD_LENGTH) throw ValidationException("password", "too_short")
        if (bytes.size > MAX_PASSWORD_LENGTH) throw ValidationException("password", "too_long")
        return raw
    }

    /** UUID в каноническом виде (нижний регистр), иначе 400 (CG-28). */
    fun uuid(raw: String, field: String): String {
        val value = raw.trim()
        if (!UUID_REGEX.matches(value)) throw ValidationException(field, "invalid_uuid")
        return runCatching { UUID.fromString(value).toString() }
            .getOrElse { throw ValidationException(field, "invalid_uuid") }
    }

    fun deviceName(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) throw ValidationException("deviceName", "empty")
        if (value.length > MAX_DEVICE_NAME_LENGTH) throw ValidationException("deviceName", "too_long")
        if (value.any { it.isISOControl() }) throw ValidationException("deviceName", "control_chars")
        return value
    }

    fun patchDeviceName(raw: String): String {
        val value = raw.trim()
        if (!PATCH_NAME_REGEX.matches(value)) {
            throw ValidationException("name", "invalid_device_name")
        }
        return value
    }

    fun osType(raw: String): OsType =
        OsType.fromWire(raw) ?: throw ValidationException("osType", "invalid_os_type")

    fun icon(raw: String): DeviceIcon =
        DeviceIcon.fromWire(raw) ?: throw ValidationException("icon", "invalid_icon")

    fun deviceType(raw: String): DeviceType =
        DeviceType.fromWire(raw) ?: throw ValidationException("deviceType", "invalid_device_type")

    /**
     * Декодирует `encryptedPayload` (base64) и проверяет размер шифротекста.
     * Границы: 28…524 316 байт (IV 12 + plaintext ≤ 512 КиБ + tag 16).
     */
    fun encryptedPayload(base64: String): ByteArray {
        if (base64.length > MAX_PAYLOAD_BASE64_CHARS) {
            throw PayloadTooLargeException(MAX_CIPHERTEXT_BYTES)
        }
        val decoded = decodeBase64(base64, "encryptedPayload")
        if (decoded.size.toLong() !in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES) {
            throw PayloadTooLargeException(MAX_CIPHERTEXT_BYTES)
        }
        return decoded
    }

    /** SHA-256 hex в нижнем регистре (CG-06: ровно 64 символа). */
    fun payloadHash(raw: String): String {
        val value = raw.trim()
        if (!HASH_REGEX.matches(value)) throw ValidationException("payloadHash", "invalid_hash")
        return value
    }

    /** Canary: base64, 24…1024 символа (DB_SCHEMA `encrypted_canary_size_chk`). */
    fun encryptedCanary(raw: String): String {
        if (raw.length !in MIN_CANARY_CHARS..MAX_CANARY_CHARS) {
            throw ValidationException("encryptedCanary", "invalid_size")
        }
        decodeBase64(raw, "encryptedCanary")
        return raw
    }

    fun fcmToken(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) throw ValidationException("fcmToken", "empty")
        if (value.length > MAX_FCM_TOKEN_LENGTH) throw ValidationException("fcmToken", "too_long")
        return value
    }

    /** target'ы: ≤10, без дублей и без собственного устройства (дизайн §4.5). */
    fun targetDeviceIds(raw: List<String>, originDeviceId: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        if (raw.size > MAX_TARGETS) throw ValidationException("targetDeviceIds", "too_many")
        val normalized = raw.map { uuid(it, "targetDeviceIds") }.distinct()
        if (normalized.any { it == originDeviceId }) {
            throw ValidationException("targetDeviceIds", "contains_origin")
        }
        return normalized
    }

    /** `0 < ts ≤ now + 5 мин` — защита от «путешествий во времени» (дизайн §4.5). */
    fun eventTimestamp(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        if (timestamp <= 0) throw ValidationException("timestamp", "invalid_timestamp")
        if (timestamp > nowMillis + TIMESTAMP_SKEW_MILLIS) {
            throw ValidationException("timestamp", "timestamp_in_future")
        }
        return timestamp
    }

    fun base64OrThrow(raw: String, field: String): ByteArray = decodeBase64(raw, field)

    /**
     * IP для колонки `inet`: принимаем только литералы, иначе null.
     * В тестовом хосте remoteAddress = "localhost", а INET такое не принимает.
     * `InetAddress` отвергает невозможные октеты и битый IPv6 (SEC-11).
     */
    fun ipOrNull(raw: String?): String? = IpAddresses.canonical(raw)

    private fun decodeBase64(raw: String, field: String): ByteArray =
        try {
            Base64.getDecoder().decode(raw)
        } catch (e: IllegalArgumentException) {
            throw ValidationException(field, "invalid_base64", e)
        }
}
