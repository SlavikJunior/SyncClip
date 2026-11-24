package io.github.slavikjunior.syncclip.server.core.push

import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteFcmTokenByToken
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.listFcmTokensForUser
import io.github.slavikjunior.syncclip.server.core.database.tx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Высокоуровневый нотификатор: отправка асинхронна и не влияет на HTTP-ответ
 * (§11.3). Ошибки логируются, но не пробрасываются; невалидные токены удаляются.
 */
class PushNotifier(
    private val databaseFactory: DatabaseFactory,
    private val sender: PushSender,
    private val scope: CoroutineScope,
    private val metrics: io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink =
        io.github.slavikjunior.syncclip.server.contracts.metrics.NoopMetricsSink,
) {
    private val logger: Logger = LoggerFactory.getLogger(PushNotifier::class.java)

    /** Ограничивает параллельные рассылки, чтобы всплеск событий не плодил корутины (§7.1). */
    private val permits = Semaphore(4)

    fun notifyUser(userId: String, message: PushMessage, exceptDeviceId: String? = null) {
        scope.launch {
            permits.withPermit {
                runCatching { notifyUserAwait(userId, message, exceptDeviceId) }
                    .onFailure { logger.warn("push failed: {}", it.javaClass.simpleName) }
            }
        }
    }

    suspend fun notifyUserAwait(userId: String, message: PushMessage, exceptDeviceId: String? = null): PushResult {
        val tokens = tx(databaseFactory.database) {
            listFcmTokensForUser(userId, exceptDeviceId)
        }
        if (tokens.isEmpty()) return PushResult(delivered = 0)
        val result = sender.send(tokens, message)
        metrics.addCounter("syncclip_push_delivered_total", result.delivered.toLong())
        metrics.addCounter("syncclip_push_invalid_tokens_total", result.invalidTokens.size.toLong())
        if (result.retryable) metrics.increment("syncclip_push_retryable_total")
        if (result.invalidTokens.isNotEmpty()) {
            tx(databaseFactory.database) {
                result.invalidTokens.forEach { deleteFcmTokenByToken(it) }
            }
            logger.info("push tokens removed: {}", result.invalidTokens.size)
        }
        return result
    }
}
