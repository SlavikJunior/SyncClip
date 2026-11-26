package io.github.slavikjunior.syncclip.core.lan

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal class LanFramesTest {

    @Test
    fun `frame round trips`() = runTest {
        val writer = MemoryLanStream()
        LanFrames.write(writer, """{"type":"PING"}""")

        val reader = MemoryLanStream()
        writer.bytes.toByteArray().let { reader.buffer.addAll(it.toList()) }

        assertEquals("""{"type":"PING"}""", LanFrames.read(reader))
    }

    @Test
    fun `empty and oversized frames are rejected`() = runTest {
        val zeroLength = MemoryLanStream().apply {
            buffer.addAll(listOf(0, 0, 0, 0))
        }
        assertNull(LanFrames.read(zeroLength))

        val tooLarge = MemoryLanStream().apply {
            // 2 МиБ: больше лимита читателя.
            buffer.addAll(listOf(0, 0x20, 0, 0))
        }
        assertNull(LanFrames.read(tooLarge))
    }

    @Test
    fun `writing above the limit fails`() = runTest {
        val stream = MemoryLanStream()
        val huge = "a".repeat(LanFrames.MAX_FRAME_BYTES + 1)

        assertFailsWith<IllegalArgumentException> {
            LanFrames.write(stream, huge)
        }
    }
}

/** Поток в памяти: кадры пишутся в [buffer] и читаются из него же. */
internal class MemoryLanStream : LanRawStream {

    val buffer: MutableList<Byte> = mutableListOf()
    private var offset = 0

    val bytes: List<Byte> get() = buffer

    override suspend fun readFully(size: Int): ByteArray? {
        if (offset + size > buffer.size) return null
        val result = ByteArray(size) { buffer[offset + it] }
        offset += size
        return result
    }

    override suspend fun write(bytes: ByteArray) {
        buffer.addAll(bytes.toList())
    }

    override suspend fun setReadTimeout(millis: Long) = Unit

    override suspend fun close() = Unit
}
