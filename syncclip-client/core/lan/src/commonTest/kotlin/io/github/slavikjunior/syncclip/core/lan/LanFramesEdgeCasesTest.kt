package io.github.slavikjunior.syncclip.core.lan

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Пограничные случаи кадрирования LAN.
 *
 * Кадр приходит из сети, то есть от недоверенной стороны: заголовок длины может
 * быть отрицательным, больше лимита или указывать за пределы буфера. Ни один
 * из этих вариантов не должен приводить к исключению или к чтению «наугад».
 */
internal class LanFramesEdgeCasesTest {

    @Test
    fun `negative length is rejected`() = runTest {
        val stream = MemoryLanStream().apply {
            buffer.addAll(listOf(0x80.toByte(), 0, 0, 0))
        }

        assertNull(LanFrames.read(stream))
    }

    @Test
    fun `length above the limit is rejected without reading the body`() = runTest {
        val stream = MemoryLanStream().apply {
            // Int.MAX_VALUE: попытка выделить такой буфер уронила бы процесс.
            buffer.addAll(listOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        }

        assertNull(LanFrames.read(stream))
    }

    @Test
    fun `incomplete header is rejected`() = runTest {
        val stream = MemoryLanStream().apply {
            buffer.addAll(listOf(0, 0, 5))
        }

        assertNull(LanFrames.read(stream))
    }

    @Test
    fun `truncated body is rejected`() = runTest {
        val stream = MemoryLanStream().apply {
            buffer.addAll(byteArrayOf(0, 0, 0, 10).toList() + "short".encodeToByteArray().toList())
        }

        assertNull(LanFrames.read(stream))
    }

    @Test
    fun `frame exactly at the limit is accepted`() = runTest {
        val text = "a".repeat(LanFrames.MAX_FRAME_BYTES)
        val writer = MemoryLanStream()
        LanFrames.write(writer, text)

        val reader = MemoryLanStream().apply { buffer.addAll(writer.bytes) }

        assertEquals(LanFrames.MAX_FRAME_BYTES, LanFrames.read(reader)?.length)
    }

    @Test
    fun `invalid utf8 payload does not throw`() = runTest {
        val payload = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41)
        val stream = MemoryLanStream().apply {
            buffer.addAll((byteArrayOf(0, 0, 0, payload.size.toByte()) + payload).toList())
        }

        // Содержимое разберёт WsCodec и вернёт null; кадрирование не падает.
        val frame = LanFrames.read(stream)

        assertTrue(frame != null)
        assertTrue(frame!!.isNotEmpty())
    }

    @Test
    fun `two frames in a row are read independently`() = runTest {
        val writer = MemoryLanStream()
        LanFrames.write(writer, "first")
        LanFrames.write(writer, "second")

        val reader = MemoryLanStream().apply { buffer.addAll(writer.bytes) }

        assertEquals("first", LanFrames.read(reader))
        assertEquals("second", LanFrames.read(reader))
        assertNull(LanFrames.read(reader))
    }
}
