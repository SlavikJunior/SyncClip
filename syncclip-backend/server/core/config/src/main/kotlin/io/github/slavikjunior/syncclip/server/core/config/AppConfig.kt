package io.github.slavikjunior.syncclip.server.core.config

/** Типизированная конфигурация приложения (дизайн §5). */
data class AppConfig(
    val environment: AppEnvironment,
    val server: ServerConfig,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val security: SecurityConfig,
    val auth: AuthConfig,
    val queue: QueueConfig,
    val push: PushConfig,
    val http: HttpConfig,
    val observability: ObservabilityConfig,
    val ws: WebSocketConfig,
    val limits: LimitsConfig,
)

/**
 * Провайдер аутентификации (SEC-10): `password`, `firebase` или `both`.
 *
 * `both` нужен смешанному парку: Android/iOS входят через Firebase SDK, а
 * Desktop (JVM без Firebase) — серверным email/паролем в тот же аккаунт
 * (Firebase-вход привязывается к существующему пользователю по email).
 */
data class AuthConfig(
    val provider: String,
    val firebaseProjectId: String?,
    val firebaseJwksUrl: String,
    val firebaseRequireVerifiedEmail: Boolean,
) {
    val isFirebase: Boolean get() = provider == "firebase" || provider == "both"
    val isPassword: Boolean get() = provider == "password" || provider == "both"
}

data class ServerConfig(
    val host: String,
    val port: Int,
    val gracefulShutdownMillis: Long,
    val requestBodyLimitBytes: Long,
)

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
    val connectionTimeoutMs: Long,
    val statementTimeoutMs: Long,
    val migrateOnStart: Boolean,
) {
    /** Секрет не должен попадать в логи через `toString()` (SEC-13). */
    override fun toString(): String =
        "DatabaseConfig(url=$url, user=$user, password=***, maxPoolSize=$maxPoolSize, " +
            "connectionTimeoutMs=$connectionTimeoutMs, statementTimeoutMs=$statementTimeoutMs, " +
            "migrateOnStart=$migrateOnStart)"
}

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val secret: String,
    val accessTtlSeconds: Long,
    val refreshTtlDays: Long,
    val clockSkewSeconds: Long,
) {
    /** Секрет не должен попадать в логи через `toString()` (SEC-13). */
    override fun toString(): String =
        "JwtConfig(issuer=$issuer, audience=$audience, secret=***, " +
            "accessTtlSeconds=$accessTtlSeconds, refreshTtlDays=$refreshTtlDays, " +
            "clockSkewSeconds=$clockSkewSeconds)"
}

data class SecurityConfig(
    val password: PasswordConfig,
    val lockout: LockoutConfig,
    val guest: GuestConfig,
    val deviceLimitFree: Int,
    val loginLockout: LoginLockoutConfig,
)

data class PasswordConfig(
    val iterations: Int,
    val saltLength: Int,
    val keyLength: Int,
)

data class LockoutConfig(
    val groupSize: Int,
    val shortLockMinutes: Long,
    val longLockHours: Long,
)

data class LoginLockoutConfig(
    val enabled: Boolean,
    val failures: Int,
    val windowMinutes: Long,
    val lockMinutes: Long,
)

data class GuestConfig(
    val sessionTtlSeconds: Long,
    val inviteTtlSeconds: Long,
)

data class QueueConfig(
    val limitPerDevice: Int,
    val ttlHours: Long,
    val cleanupIntervalSeconds: Long,
)

data class PushConfig(
    val provider: String,
    val credentialsPath: String?,
    val projectId: String?,
)

data class HttpConfig(
    /** Доверенные адреса/CIDR reverse proxy; заголовки XFF читаются только от них. */
    val trustedProxies: List<String>,
)

data class ObservabilityConfig(
    val logLevel: String,
    val jsonLogs: Boolean,
    val metricsEnabled: Boolean,
    val metricsToken: String?,
) {
    /** Токен не должен попадать в логи через `toString()`. */
    override fun toString(): String =
        "ObservabilityConfig(logLevel=$logLevel, jsonLogs=$jsonLogs, " +
            "metricsEnabled=$metricsEnabled, metricsToken=${if (metricsToken == null) "null" else "***"})"
}

data class WebSocketConfig(
    val allowQueryToken: Boolean,
    val outboundCapacity: Int,
    val maxInflightBytes: Long,
    val maxInitialPendingBytes: Long,
    val maxConnectionsPerUser: Int,
    val maxConnectionsPerDevice: Int,
    val pingPeriodSeconds: Long,
    val timeoutSeconds: Long,
    val maxFrameBytes: Long,
)

data class LimitsConfig(
    val enabled: Boolean,
    val loginPerMinute: Int,
    val registerPerMinute: Int,
    val refreshPerMinute: Int,
    val logoutPerMinute: Int,
    val canaryPerMinute: Int,
    val canaryAttemptPerMinute: Int,
    val guestInvitePerHour: Int,
    val guestExchangePerMinute: Int,
    val defaultPerMinute: Int,
    val pendingPerMinute: Int,
    val syncEventsPerMinute: Int,
    val acksPerMinute: Int,
    val pingPerMinute: Int,
)
