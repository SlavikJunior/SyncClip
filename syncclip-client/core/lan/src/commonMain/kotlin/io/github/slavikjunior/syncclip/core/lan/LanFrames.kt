package io.github.slavikjunior.syncclip.core.lan

/**
 * Кадр LAN-протокола: `4 байта длины (big-endian) + UTF-8 JSON`.
 *
 * Лимит 1 МиБ защищает от чтения огромного кадра по ошибке/атаке: полезная
 * нагрузка ограничена 512 КиБ паспортом, base64 добавляет треть.
 */
public object LanFrames {

    public const val MAX_FRAME_BYTES: Int = 1024 * 1024

    public suspend fun read(stream: LanRawStream): String? {
        val header = stream.readFully(HEADER_BYTES) ?: return null
        val length = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        if (length <= 0 || length > MAX_FRAME_BYTES) return null
        return stream.readFully(length)?.decodeToString()
    }

    public suspend fun write(stream: LanRawStream, text: String) {
        val payload = text.encodeToByteArray()
        require(payload.size <= MAX_FRAME_BYTES) { "LAN frame is too large" }
        val header = byteArrayOf(
            (payload.size ushr 24).toByte(),
            (payload.size ushr 16).toByte(),
            (payload.size ushr 8).toByte(),
            payload.size.toByte(),
        )
        stream.write(header + payload)
    }

    private const val HEADER_BYTES = 4
}
