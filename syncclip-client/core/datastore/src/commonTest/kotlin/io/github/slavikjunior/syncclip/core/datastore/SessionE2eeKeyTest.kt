package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class SessionE2eeKeyTest {

    private val storage = InMemoryKeyStorage()
    private val store = SecureE2eeKey(storage)

    @Test
    fun `key is absent before unlock and after clear`() = runTest {
        store.hydrate()
        assertNull(store.key())

        val key = ByteArray(32) { it.toByte() }
        store.setKey(key)
        assertNotNull(store.key())

        store.clear()
        assertNull(store.key())
        assertNull(storage.value)
    }

    @Test
    fun `stored key is restored by hydrate`() = runTest {
        val key = ByteArray(32) { (it + 7).toByte() }
        storage.value = key.copyOf()

        store.hydrate()

        assertContentEquals(key, assertNotNull(store.key()))
    }

    @Test
    fun `key is copied on the way in and out`() = runTest {
        val key = ByteArray(32) { it.toByte() }
        store.setKey(key)
        key[0] = 99

        val stored = assertNotNull(store.key())
        assertContentEquals(ByteArray(32) { it.toByte() }, stored)

        stored[1] = 42
        assertContentEquals(ByteArray(32) { it.toByte() }, assertNotNull(store.key()))
    }

    @Test
    fun `broken storage does not block the app`() = runTest {
        storage.failingLoad = true

        store.hydrate()

        assertNull(store.key())
    }

    @Test
    fun `setKey rejects wrong key size`() = runTest {
        val error = runCatching { store.setKey(ByteArray(16)) }.exceptionOrNull()

        assertNotNull(error)
    }
}

/** Хранилище в памяти: проверяет связку store ↔ storage без платформы. */
internal class InMemoryKeyStorage(
    var value: ByteArray? = null,
    var failingLoad: Boolean = false,
) : E2eeKeyStorage {

    override suspend fun load(): ByteArray? {
        if (failingLoad) error("keystore is broken")
        return value?.copyOf()
    }

    override suspend fun save(key: ByteArray) {
        value = key.copyOf()
    }

    override suspend fun clear() {
        value = null
    }
}
