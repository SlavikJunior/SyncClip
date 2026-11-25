package io.github.slavikjunior.syncclip.shared.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Вектор для iOS Share Extension: расширение шифрует CryptoKit-ом, поэтому
 * байты обязаны совпадать с Kotlin-движком. Тот же вектор зафиксирован
 * в `app/iosApp/ShareExtension/README.md` и сверяется при изменении Swift-кода.
 *
 * Ключ — 0x00…0x1F, IV — 12 нулей, текст — `share-vector`.
 */
internal class AesGcmShareVectorTest {

    private val key = ByteArray(32) { it.toByte() }
    private val iv = ByteArray(12)
    private val text = "share-vector"

    @Test
    fun `share extension payload matches the fixed vector`() {
        val cipher = AesGcmPayloadCipher(ivSource = { iv })

        assertEquals(
            "AAAAAAAAAAAAAAAAfdTUrNAB9dhr3MZHbLP7X/6yAFH5pRTY1gNB9w==",
            cipher.encrypt(text, key),
        )
        assertEquals(
            "28b49c73818b0b7fdcae59de64c4df11f89f186df42ae27809fe7c481bc595eb",
            PayloadHash.of(text),
        )
    }
}
