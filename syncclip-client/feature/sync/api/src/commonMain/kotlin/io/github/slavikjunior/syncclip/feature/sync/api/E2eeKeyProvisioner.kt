package io.github.slavikjunior.syncclip.feature.sync.api

/**
 * Разблокировка E2EE мастер-паролем (FR-1.2, FR-1.3).
 *
 * Первое устройство создаёт canary на сервере, остальные проверяют по нему
 * пароль. Ключ не покидает клиент: сервер видит только шифротекст.
 */
public interface E2eeKeyProvisioner {

    /**
     * @return [E2eeUnlockResult.Success] с 32-байтным ключом, если пароль
     *   подтверждён canary (или canary только что создан).
     */
    public suspend fun unlock(masterPassword: String): E2eeUnlockResult
}
