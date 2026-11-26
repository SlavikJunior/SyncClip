package io.github.slavikjunior.syncclip.core.lan

/**
 * Байтовый поток одного соединения.
 *
 * Платформа отвечает только за чтение/запись; кадрирование и таймауты — в
 * [LanFrames], чтобы поведение было одинаковым на всех ОС.
 */
public interface LanRawStream {

    /** Ровно [size] байт или `null`, если соединение закрыто до конца кадра. */
    public suspend fun readFully(size: Int): ByteArray?

    public suspend fun write(bytes: ByteArray)

    /** Таймаут ожидания ACK; после connect остаётся бюджет из 300 мс. */
    public suspend fun setReadTimeout(millis: Long)

    public suspend fun close()
}
