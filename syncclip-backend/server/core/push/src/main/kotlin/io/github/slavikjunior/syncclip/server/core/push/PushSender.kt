package io.github.slavikjunior.syncclip.server.core.push

/** Провайдер push: fcm или noop (§11.4). */
interface PushSender {
    suspend fun send(tokens: List<String>, message: PushMessage): PushResult

    companion object {
        const val MAX_TOKENS_PER_CALL = 500
    }
}
