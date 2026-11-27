package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient

/**
 * Открывает `/v1/sync` на текущем устройстве.
 *
 * `deviceId` запрашивается у хранилища при каждом подключении: сервер
 * сверяет query-параметр с claim `dev` и закрывает соединение при расхождении.
 */
internal class KtorSyncSocketFactory(
    private val http: SyncClipHttpClient,
    private val deviceId: suspend () -> String,
) : SyncSocketFactory {

    override suspend fun open(): SyncSocket {
        val session = http.openWebSocket("$SYNC_PATH?deviceId=${deviceId()}")
        return KtorSyncSocket(session)
    }

    private companion object {
        const val SYNC_PATH = "/v1/sync"
    }
}
