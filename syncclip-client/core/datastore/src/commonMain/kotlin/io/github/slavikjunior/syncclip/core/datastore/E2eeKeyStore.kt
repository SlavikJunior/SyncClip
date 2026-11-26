package io.github.slavikjunior.syncclip.core.datastore

import io.github.slavikjunior.syncclip.shared.crypto.E2eeKeyProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * Ключ E2EE текущего пользователя.
 *
 * Ключ хранится в защищённом хранилище платформы (Keystore/Keychain/файл с
 * правами владельца) и подгружается при старте: повторный ввод мастер-пароля
 * нужен только если ключа ещё нет и canary не подтверждён.
 */
public interface E2eeKeyStore : E2eeKeyProvider {

    /** `null` — ключа нет (первый запуск или хранилище очищено). */
    public val keyState: StateFlow<ByteArray?>

    /**
     * Читает ключ из хранилища один раз за процесс.
     *
     * Отдельный метод нужен UI: до завершения чтения нельзя решать, показывать
     * экран мастер-пароля или сразу основной экран.
     */
    public suspend fun hydrate()

    public suspend fun setKey(key: ByteArray)

    public suspend fun clear()
}
