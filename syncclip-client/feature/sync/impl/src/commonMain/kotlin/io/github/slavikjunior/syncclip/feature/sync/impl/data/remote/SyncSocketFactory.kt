package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

/** Открывает соединение с сервером; брошенное исключение — попытка реконнекта. */
internal fun interface SyncSocketFactory {

    suspend fun open(): SyncSocket
}
