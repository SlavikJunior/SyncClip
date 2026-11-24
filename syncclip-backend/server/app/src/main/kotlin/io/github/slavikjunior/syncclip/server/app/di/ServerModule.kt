package io.github.slavikjunior.syncclip.server.app.di

import io.github.slavikjunior.syncclip.server.app.i18n.MessageCatalog
import io.github.slavikjunior.syncclip.server.app.jobs.MaintenanceScheduler
import io.github.slavikjunior.syncclip.server.app.metrics.MetricsRegistry
import io.github.slavikjunior.syncclip.server.app.plugins.ServerJson
import io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink
import io.github.slavikjunior.syncclip.server.contracts.port.UserSessionGateway
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.core.push.FcmPushSender
import io.github.slavikjunior.syncclip.server.core.push.NoopPushSender
import io.github.slavikjunior.syncclip.server.core.push.PushNotifier
import io.github.slavikjunior.syncclip.server.core.push.PushSender
import io.github.slavikjunior.syncclip.server.core.security.ApiRateLimiter
import io.github.slavikjunior.syncclip.server.core.security.AuditHasher
import io.github.slavikjunior.syncclip.server.core.security.JwtIssuer
import io.github.slavikjunior.syncclip.server.core.security.JwtVerifier
import io.github.slavikjunior.syncclip.server.core.security.LockoutPolicy
import io.github.slavikjunior.syncclip.server.core.security.PasswordHasher
import io.github.slavikjunior.syncclip.server.core.security.Pbkdf2PasswordHasher
import io.github.slavikjunior.syncclip.server.core.security.SecureTokens
import io.github.slavikjunior.syncclip.server.feature.auth.domain.AuthService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.FirebaseVerifier
import io.github.slavikjunior.syncclip.server.feature.auth.domain.GuestService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.JwksFirebaseVerifier
import io.github.slavikjunior.syncclip.server.feature.auth.domain.MasterPasswordService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.SessionService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.SessionTokenStore
import io.github.slavikjunior.syncclip.server.feature.devices.domain.DeviceService
import io.github.slavikjunior.syncclip.server.feature.notifications.domain.FcmRegistrationService
import io.github.slavikjunior.syncclip.server.feature.notifications.domain.UnreadFeedService
import io.github.slavikjunior.syncclip.server.feature.sync.domain.OfflineQueueService
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SessionRegistry
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.time.Duration

/**
 * Composition root в Koin (R-5): только здесь собирается граф.
 * Сервисы фич публичны (осознанное смягчение INV-26, §3.2), репозитории —
 * stateless top-level функции над транзакцией.
 *
 * Все зависимости именованы: позиционный `get()` ломается при изменении
 * порядка параметров (ARCH-03).
 */
fun serverModule(config: AppConfig, databaseFactory: DatabaseFactory) = module {

    single { config }
    single { databaseFactory }
    single<Json> { ServerJson }
    single { MessageCatalog() }
    single { SecureTokens() }
    single { AuditHasher(config.jwt.secret) }
    single { MetricsRegistry() }
    single<MetricsSink> { get<MetricsRegistry>() }

    single<PasswordHasher> {
        Pbkdf2PasswordHasher(
            iterations = config.security.password.iterations,
            saltLength = config.security.password.saltLength,
            keyLength = config.security.password.keyLength,
        )
    }
    single { JwtIssuer(config = config.jwt, tokens = get()) }
    single { JwtVerifier(config.jwt) }
    single<FirebaseVerifier> {
        JwksFirebaseVerifier(
            projectId = config.auth.firebaseProjectId.orEmpty(),
            jwksUrl = config.auth.firebaseJwksUrl,
        )
    }
    single { ApiRateLimiter(config.limits) }
    single {
        LockoutPolicy(
            groupSize = config.security.lockout.groupSize,
            shortLock = Duration.ofMinutes(config.security.lockout.shortLockMinutes),
            longLock = Duration.ofHours(config.security.lockout.longLockHours),
        )
    }

    single<PushSender> {
        if (config.push.provider == "fcm") {
            FcmPushSender(
                credentialsPath = requireNotNull(config.push.credentialsPath) {
                    "FCM_CREDENTIALS_PATH is required when PUSH_PROVIDER=fcm"
                },
                projectIdOverride = config.push.projectId,
            )
        } else {
            NoopPushSender()
        }
    }
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single { PushNotifier(databaseFactory = get(), sender = get(), scope = get(), metrics = get()) }

    single {
        SessionRegistry(
            wsConfig = config.ws,
            limits = config.limits,
            json = get(),
            tokens = get(),
            metrics = get(),
        )
    }
    single<UserSessionGateway> { get<SessionRegistry>() }

    single { DeviceAuthenticator(databaseFactory = get()) }
    single { OfflineQueueService(databaseFactory = get(), config = config) }
    single {
        SyncService(
            databaseFactory = get(),
            queue = get(),
            sessionGateway = get(),
            metrics = get(),
        )
    }

    single {
        AuthService(
            databaseFactory = get(),
            hasher = get(),
            auditHasher = get(),
            tokenStore = get(),
            config = config,
            pushNotifier = get(),
            metrics = get(),
        )
    }
    single { SessionTokenStore(issuer = get(), config = config) }
    single {
        SessionService(
            databaseFactory = get(),
            tokenStore = get(),
            verifier = get(),
            sessionGateway = get(),
            metrics = get(),
        )
    }
    single {
        MasterPasswordService(
            databaseFactory = get(),
            config = config,
            policy = get(),
            pushNotifier = get(),
            sessionGateway = get(),
        )
    }
    single {
        GuestService(
            databaseFactory = get(),
            config = config,
            issuer = get(),
            tokens = get(),
        )
    }
    single { DeviceService(databaseFactory = get(), sessionGateway = get()) }
    single { FcmRegistrationService(databaseFactory = get()) }
    single { UnreadFeedService(databaseFactory = get()) }

    single {
        MaintenanceScheduler(
            databaseFactory = get(),
            queue = get(),
            sessionGateway = get(),
            rateLimiter = get(),
            config = config,
            sessionRegistry = get(),
        )
    }
}
