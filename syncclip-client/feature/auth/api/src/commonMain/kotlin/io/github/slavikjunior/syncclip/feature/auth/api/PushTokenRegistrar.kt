package io.github.slavikjunior.syncclip.feature.auth.api

/**
 * Регистрация FCM-токена текущего устройства на сервере.
 *
 * Контракт нужен composition root: при старте приложения и при ротации токена
 * push нужно переотправить, даже если экран входа не открывался.
 */
public interface PushTokenRegistrar {
    public suspend fun registerCurrentToken()
}
