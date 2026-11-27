package io.github.slavikjunior.syncclip.shared.network

/**
 * Проверка срока действия JWT access-токена.
 *
 * Токен TTL 1 час, сервер не продлевает WS-соединение, но при переподключении
 * просроченный JWT даёт 401. Клиент читает claim `exp` (без верификации
 * подписи: подпись проверяет сервер) и обновляет пару заранее.
 */
public object JwtExpiry {

    /** Запас до истечения, чтобы не успеть подключиться «в последнюю секунду». */
    public const val DEFAULT_SKEW_MILLIS: Long = 60_000

    /**
     * @param token `null`/мусор — токена нет, нужен refresh.
     * @return `true`, если до истечения больше [skewMillis].
     */
    public fun isFresh(
        token: String?,
        nowMillis: Long,
        skewMillis: Long = DEFAULT_SKEW_MILLIS,
    ): Boolean {
        val expiresAt = expiresAtMillis(token ?: return false) ?: return false
        return expiresAt - nowMillis > skewMillis
    }

    /** Время истечения из claim `exp` в миллисекундах или `null`. */
    public fun expiresAtMillis(token: String): Long? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val payload = decodeBase64Url(parts[1]) ?: return null
        val json = payload.decodeToString()
        val match = EXP_REGEX.find(json) ?: return null
        val seconds = match.groupValues[1].toLongOrNull() ?: return null
        return seconds * 1_000
    }

    private fun decodeBase64Url(text: String): ByteArray? {
        val normalized = text.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            0 -> normalized
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> return null
        }
        return runCatching { javaBase64Decode(padded) }.getOrNull()
    }

    private val EXP_REGEX = Regex("\"exp\"\\s*:\\s*(\\d+)")

    /**
     * Base64 в common-коде: `kotlin.io.encoding.Base64` был экспериментальным,
     * а алфавит JWT фиксирован. Декодер минимальный, только для payload.
     */
    private fun javaBase64Decode(text: String): ByteArray {
        val output = ArrayList<Byte>(text.length / 4 * 3)
        var index = 0
        while (index < text.length) {
            var buffer = 0
            var bits = 0
            var symbol = 0
            while (symbol < 4 && index < text.length) {
                val char = text[index++]
                if (char == '=') break
                val value = ALPHABET.indexOf(char)
                require(value >= 0) { "invalid base64" }
                buffer = (buffer shl 6) or value
                bits += 6
                symbol++
                if (bits >= 8) {
                    bits -= 8
                    output.add(((buffer shr bits) and 0xFF).toByte())
                }
            }
        }
        return output.toByteArray()
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
}
