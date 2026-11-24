package io.github.slavikjunior.syncclip.server.app.jobs

import io.github.slavikjunior.syncclip.server.contracts.port.UserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.ws.RevocationReason
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteAndListExpiredGuests
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteAttemptsOlderThan
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteExpiredRefreshTokens
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteRevokedRefreshTokensBefore
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteStaleGuestInvites
import io.github.slavikjunior.syncclip.server.core.database.tx
import io.github.slavikjunior.syncclip.server.core.security.ApiRateLimiter
import io.github.slavikjunior.syncclip.server.feature.sync.domain.OfflineQueueService
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Фоновые задачи (§9.5, §7.7, §7.4): TTL очереди, истечение гостей,
 * старые refresh-токены, retention auth_attempts, sweep лимитера.
 *
 * Все DELETE батчевые (PERF-05), иначе большая таблица держит лок и
 * раздувает WAL одним запросом.
 */
class MaintenanceScheduler(
    private val databaseFactory: DatabaseFactory,
    private val queue: OfflineQueueService,
    private val sessionGateway: UserSessionGateway,
    private val rateLimiter: ApiRateLimiter,
    private val config: AppConfig,
    private val sessionRegistry: SessionRegistry,
    private val clock: () -> Instant = Instant::now,
) {
    private val logger = LoggerFactory.getLogger(MaintenanceScheduler::class.java)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        val intervalMillis = config.queue.cleanupIntervalSeconds.coerceAtLeast(1) * 1000
        job = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                runCatching { runOnce() }
                    .onFailure { logger.warn("maintenance cycle failed: {}", it.javaClass.simpleName) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun runOnce() {
        val now = clock()
        val expiredPackets = queue.cleanupExpired()
        if (expiredPackets > 0) logger.info("queue expired packets deleted: {}", expiredPackets)

        val expiredGuests = tx(databaseFactory.database) { deleteAndListExpiredGuests(now) }
        expiredGuests.forEach { guest ->
            sessionGateway.revokeDevice(guest.userId, guest.deviceId, RevocationReason.GUEST_SESSION_EXPIRED)
        }
        if (expiredGuests.isNotEmpty()) logger.info("guest devices expired: {}", expiredGuests.size)

        drainBatches { tx(databaseFactory.database) { deleteExpiredRefreshTokens(now) } }
        drainBatches {
            tx(databaseFactory.database) {
                deleteRevokedRefreshTokensBefore(now.minus(REVOKED_REFRESH_RETENTION))
            }
        }
        drainBatches { tx(databaseFactory.database) { deleteAttemptsOlderThan(now.minus(ATTEMPT_RETENTION)) } }
        drainBatches {
            tx(databaseFactory.database) { deleteStaleGuestInvites(now.minus(INVITE_RETENTION)) }
        }

        // Read-only пересчёт статуса миграций: health/ready не делает DDL (PERF-04).
        databaseFactory.refreshMigrationStatus()
        rateLimiter.sweep()
        sessionRegistry.sweepLimiters()
    }

    /** Удаляем максимум `MAX_BATCHES_PER_CYCLE` пачек, остаток — следующим циклом. */
    private suspend fun drainBatches(deleteBatch: suspend () -> Int) {
        repeat(MAX_BATCHES_PER_CYCLE) {
            val deleted = deleteBatch()
            if (deleted < BATCH_SIZE) return
        }
    }

    private companion object {
        const val BATCH_SIZE = 1_000
        const val MAX_BATCHES_PER_CYCLE = 10
        val ATTEMPT_RETENTION: Duration = Duration.ofDays(30)
        val REVOKED_REFRESH_RETENTION: Duration = Duration.ofDays(7)
        val INVITE_RETENTION: Duration = Duration.ofDays(1)
    }
}
