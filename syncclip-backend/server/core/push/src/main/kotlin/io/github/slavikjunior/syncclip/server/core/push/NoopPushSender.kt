package io.github.slavikjunior.syncclip.server.core.push

import org.slf4j.LoggerFactory

/** dev/test: пишет факт отправки без PII и без сети. */
class NoopPushSender : PushSender {

    private val logger = LoggerFactory.getLogger(NoopPushSender::class.java)

    override suspend fun send(tokens: List<String>, message: PushMessage): PushResult {
        logger.info("push disabled (noop): tokens={} collapseKey={}", tokens.size, message.collapseKey)
        return PushResult(delivered = tokens.size)
    }
}
