package io.github.slavikjunior.syncclip.core.datastore

/**
 * Мост к iOS Share Extension.
 *
 * Расширение — отдельный процесс без DataStore и сессии, поэтому ему нужны
 * только access-токен, `deviceId` и адрес сервера. Значения кладутся в общий
 * App Group `NSUserDefaults` при каждой записи сессии; на Android/Desktop
 * реализация — no-op.
 *
 * Refresh-токен расширению не нужен: ротацию выполняет приложение (иначе
 * refresh-семья разошлась бы между процессами).
 */
public interface ShareBridge {

    /** `null` означает «сессии нет» — очередь расширения ждёт следующего входа. */
    public fun session(userId: String?, accessToken: String?)

    public fun device(deviceId: String)

    public fun endpoint(baseUrl: String)
}

internal expect fun shareBridge(): ShareBridge

/** Публикует адрес API для Share Extension; вне iOS ничего не делает. */
public fun publishSharedEndpoint(baseUrl: String) {
    shareBridge().endpoint(baseUrl)
}
