package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow

/** Обёртка Ktor-сессии: наружу видны только текст и закрытие. */
internal class KtorSyncSocket(
    private val session: DefaultClientWebSocketSession,
) : SyncSocket {

    override suspend fun send(text: String) {
        session.send(Frame.Text(text))
    }

    override fun incoming(): Flow<String> =
        session.incoming.receiveAsFlow().mapNotNull { frame ->
            (frame as? Frame.Text)?.readText()
        }

    override suspend fun close() {
        // Сессия может быть уже закрыта сервером — это штатное завершение.
        runCatching { session.close() }
    }
}
