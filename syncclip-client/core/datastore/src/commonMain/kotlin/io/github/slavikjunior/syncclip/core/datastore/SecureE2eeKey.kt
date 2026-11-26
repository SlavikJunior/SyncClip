package io.github.slavikjunior.syncclip.core.datastore

import io.github.slavikjunior.syncclip.shared.crypto.Pbkdf2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ключ E2EE поверх защищённого хранилища платформы.
 *
 * Массив копируется на входе и выходе: вызывающий код не должен иметь
 * возможности изменить ключ, который уже используется транспортом.
 *
 * [hydrate] идемпотентен: параллельные вызовы сериализуются мьютексом, иначе
 * два стартовых экрана прочитали бы хранилище дважды.
 */
internal class SecureE2eeKey(
    private val storage: E2eeKeyStorage,
) : E2eeKeyStore {

    private val state = MutableStateFlow<ByteArray?>(null)
    private val mutex = Mutex()
    private var hydrated = false

    override val keyState: StateFlow<ByteArray?> = state.asStateFlow()

    override suspend fun hydrate() {
        mutex.withLock {
            if (hydrated) return
            // Повреждённый Keystore/Keychain не должен валить приложение:
            // трактуем как «ключа нет» и просим мастер-пароль.
            state.value = runCatching { storage.load() }.getOrNull()?.copyOf()
            hydrated = true
        }
    }

    override suspend fun key(): ByteArray? {
        hydrate()
        return state.value?.copyOf()
    }

    override suspend fun setKey(key: ByteArray) {
        require(key.size == Pbkdf2.KEY_BYTES) {
            "E2EE key must be ${Pbkdf2.KEY_BYTES} bytes"
        }
        // Сбой Keystore/Keychain не должен ломать текущую сессию: ключ останется
        // в памяти, а при следующем запуске пользователь введёт пароль снова.
        runCatching { storage.save(key) }
        state.value = key.copyOf()
        hydrated = true
    }

    override suspend fun clear() {
        storage.clear()
        state.value = null
        hydrated = true
    }
}
