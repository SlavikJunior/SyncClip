package io.github.slavikjunior.syncclip.server.core.config

import io.github.slavikjunior.syncclip.server.contracts.validation.IpAddresses
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fail-fast правила §5.3 дизайна + hardening аудита (OPS-01).
 * Значения секретов в сообщения не попадают — только имена переменных и правило.
 */
object ConfigValidation {

    private val WEAK_JWT_SECRETS = setOf("change-me", "secret", "test", "changeme", "password")
    private val LOG_LEVELS = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")

    fun validate(config: AppConfig) {
        val problems = mutableListOf<String>()

        validateJwt(config, problems)
        validateDatabase(config, problems)
        validateServer(config, problems)
        validateLimits(config, problems)
        validateSecurity(config, problems)
        validateAuth(config, problems)
        validateQueue(config, problems)
        validatePush(config, problems)
        validateLogging(config, problems)
        validateTrustedProxies(config, problems)

        if (problems.isNotEmpty()) throw ConfigValidationException(problems)
    }

    private fun validateJwt(config: AppConfig, problems: MutableList<String>) {
        val secret = config.jwt.secret
        if (secret.toByteArray(Charsets.UTF_8).size < 32) {
            problems += "JWT_SECRET: must be at least 32 bytes"
        }
        if (secret.lowercase() in WEAK_JWT_SECRETS) {
            problems += "JWT_SECRET: value is in the weak-secret block list"
        }
        if (secret.isNotEmpty() && secret.toSet().size < 8) {
            problems += "JWT_SECRET: low entropy (at least 8 distinct characters required)"
        }
        if (config.jwt.issuer.isBlank()) problems += "JWT_ISSUER: required"
        if (config.jwt.audience.isBlank()) problems += "JWT_AUDIENCE: required"
        if (config.jwt.accessTtlSeconds !in 60..86_400) {
            problems += "JWT_ACCESS_TTL_SECONDS: must be within 60..86400"
        }
        if (config.jwt.refreshTtlDays !in 1..365) {
            problems += "JWT_REFRESH_TTL_DAYS: must be within 1..365"
        }
        if (config.jwt.clockSkewSeconds !in 0..300) {
            problems += "JWT_CLOCK_SKEW_SECONDS: must be within 0..300"
        }
    }

    private fun validateDatabase(config: AppConfig, problems: MutableList<String>) {
        if (config.database.url.isBlank()) {
            problems += "DB_URL: required"
        } else if (!config.database.url.startsWith("jdbc:postgresql:")) {
            problems += "DB_URL: must start with jdbc:postgresql:"
        }
        if (config.database.user.isBlank()) problems += "DB_USER: required"
        if (config.database.password.isBlank()) problems += "DB_PASSWORD: required"
        if (config.database.maxPoolSize !in 1..50) {
            problems += "DB_POOL_SIZE: must be within 1..50"
        }
        if (config.database.connectionTimeoutMs !in 500..60_000) {
            problems += "DB_CONNECTION_TIMEOUT_MS: must be within 500..60000"
        }
        if (config.database.statementTimeoutMs !in 500..120_000) {
            problems += "DB_STATEMENT_TIMEOUT_MS: must be within 500..120000"
        }
    }

    private fun validateServer(config: AppConfig, problems: MutableList<String>) {
        if (config.server.host.isBlank()) problems += "SERVER_HOST: required"
        if (config.server.port !in 1..65_535) {
            problems += "SERVER_PORT: must be within 1..65535"
        }
        if (config.server.requestBodyLimitBytes !in 1_024..16_777_216) {
            problems += "REQUEST_BODY_LIMIT_BYTES: must be within 1024..16777216"
        }
        if (config.server.gracefulShutdownMillis !in 0..120_000) {
            problems += "GRACEFUL_SHUTDOWN_MILLIS: must be within 0..120000"
        }
    }

    private fun validateLimits(config: AppConfig, problems: MutableList<String>) {
        val limits = config.limits
        val values = mapOf(
            "RATE_LIMIT_LOGIN_PER_MINUTE" to limits.loginPerMinute,
            "RATE_LIMIT_REGISTER_PER_MINUTE" to limits.registerPerMinute,
            "RATE_LIMIT_REFRESH_PER_MINUTE" to limits.refreshPerMinute,
            "RATE_LIMIT_LOGOUT_PER_MINUTE" to limits.logoutPerMinute,
            "RATE_LIMIT_CANARY_PER_MINUTE" to limits.canaryPerMinute,
            "RATE_LIMIT_CANARY_ATTEMPT_PER_MINUTE" to limits.canaryAttemptPerMinute,
            "RATE_LIMIT_GUEST_INVITE_PER_HOUR" to limits.guestInvitePerHour,
            "RATE_LIMIT_GUEST_EXCHANGE_PER_MINUTE" to limits.guestExchangePerMinute,
            "RATE_LIMIT_DEFAULT_PER_MINUTE" to limits.defaultPerMinute,
            "RATE_LIMIT_PENDING_PER_MINUTE" to limits.pendingPerMinute,
            "RATE_LIMIT_SYNC_EVENTS_PER_MINUTE" to limits.syncEventsPerMinute,
            "RATE_LIMIT_ACKS_PER_MINUTE" to limits.acksPerMinute,
            "RATE_LIMIT_PING_PER_MINUTE" to limits.pingPerMinute,
        )
        values.forEach { (name, value) ->
            if (value !in 1..1_000_000) problems += "$name: must be within 1..1000000"
        }
        val ws = config.ws
        if (ws.outboundCapacity !in 1..100) problems += "WS_OUTBOUND_CAPACITY: must be within 1..100"
        if (ws.maxInflightBytes !in 65_536..67_108_864) {
            problems += "WS_MAX_INFLIGHT_BYTES: must be within 65536..67108864"
        }
        if (ws.maxInitialPendingBytes !in 0..67_108_864) {
            problems += "WS_MAX_INITIAL_PENDING_BYTES: must be within 0..67108864"
        }
        if (ws.maxConnectionsPerDevice !in 1..20) {
            problems += "WS_MAX_CONNECTIONS_PER_DEVICE: must be within 1..20"
        }
        if (ws.maxConnectionsPerUser !in ws.maxConnectionsPerDevice..200) {
            problems += "WS_MAX_CONNECTIONS_PER_USER: must be within WS_MAX_CONNECTIONS_PER_DEVICE..200"
        }
        if (ws.pingPeriodSeconds !in 5..300) {
            problems += "WS_PING_PERIOD_SECONDS: must be within 5..300"
        }
        if (ws.timeoutSeconds !in 1..120) {
            problems += "WS_TIMEOUT_SECONDS: must be within 1..120"
        }
        if (ws.maxFrameBytes !in 1_024..16_777_216) {
            problems += "WS_MAX_FRAME_BYTES: must be within 1024..16777216"
        }
    }

    private fun validateSecurity(config: AppConfig, problems: MutableList<String>) {
        if (config.security.password.iterations !in 10_000..2_000_000) {
            problems += "PASSWORD_HASH_ITERATIONS: must be within 10000..2000000"
        }
        if (config.security.password.saltLength !in 8..64) {
            problems += "PASSWORD_SALT_LENGTH: must be within 8..64"
        }
        if (config.security.password.keyLength !in 16..64) {
            problems += "PASSWORD_KEY_LENGTH: must be within 16..64"
        }
        if (config.security.deviceLimitFree !in 1..10) {
            problems += "DEVICE_LIMIT_FREE: must be within 1..10"
        }
        if (config.security.lockout.groupSize !in 1..10) {
            problems += "LOCKOUT_GROUP_SIZE: must be within 1..10"
        }
        if (config.security.lockout.shortLockMinutes <= 0) {
            problems += "LOCKOUT_SHORT_MINUTES: must be > 0"
        }
        if (config.security.lockout.longLockHours <= 0) {
            problems += "LOCKOUT_LONG_HOURS: must be > 0"
        }
        if (config.security.guest.sessionTtlSeconds !in 60..86_400) {
            problems += "GUEST_SESSION_TTL_SECONDS: must be within 60..86400"
        }
        if (config.security.guest.inviteTtlSeconds !in 60..86_400) {
            problems += "GUEST_INVITE_TTL_SECONDS: must be within 60..86400"
        }
        val loginLockout = config.security.loginLockout
        if (loginLockout.enabled) {
            if (loginLockout.failures !in 1..1_000) {
                problems += "LOGIN_LOCKOUT_FAILURES: must be within 1..1000"
            }
            if (loginLockout.windowMinutes !in 1..1_440) {
                problems += "LOGIN_LOCKOUT_WINDOW_MINUTES: must be within 1..1440"
            }
            if (loginLockout.lockMinutes !in 1..10_080) {
                problems += "LOGIN_LOCKOUT_MINUTES: must be within 1..10080"
            }
        }
    }

    private fun validateAuth(config: AppConfig, problems: MutableList<String>) {
        if (config.auth.provider !in setOf("password", "firebase")) {
            problems += "AUTH_PROVIDER: must be password or firebase"
        }
        if (config.auth.isFirebase) {
            if (config.auth.firebaseProjectId.isNullOrBlank()) {
                problems += "FIREBASE_PROJECT_ID: required when AUTH_PROVIDER=firebase"
            }
            val url = config.auth.firebaseJwksUrl
            val httpAllowedInDev = config.environment == AppEnvironment.DEV && url.startsWith("http://")
            if (!url.startsWith("https://") && !httpAllowedInDev) {
                problems += "FIREBASE_JWKS_URL: must be https (http allowed only in DEV for tests)"
            }
        }
    }

    private fun validateQueue(config: AppConfig, problems: MutableList<String>) {
        if (config.queue.limitPerDevice !in 1..100) {
            problems += "OFFLINE_QUEUE_LIMIT: must be within 1..100"
        }
        if (config.queue.ttlHours !in 1..168) {
            problems += "OFFLINE_QUEUE_TTL_HOURS: must be within 1..168"
        }
        // 0 превращает планировщик в busy-loop (OPS-01).
        if (config.queue.cleanupIntervalSeconds !in 1..86_400) {
            problems += "QUEUE_CLEANUP_INTERVAL_SECONDS: must be within 1..86400"
        }
    }

    private fun validatePush(config: AppConfig, problems: MutableList<String>) {
        if (config.push.provider !in setOf("noop", "fcm")) {
            problems += "PUSH_PROVIDER: must be noop or fcm"
        }
        if (config.push.provider == "fcm") {
            val path = config.push.credentialsPath
            if (path.isNullOrBlank()) {
                problems += "FCM_CREDENTIALS_PATH: required when PUSH_PROVIDER=fcm"
            } else if (config.environment == AppEnvironment.PROD && !Files.isReadable(Path.of(path))) {
                problems += "FCM_CREDENTIALS_PATH: file is not readable in PROD"
            }
        }
    }

    private fun validateLogging(config: AppConfig, problems: MutableList<String>) {
        if (config.observability.logLevel !in LOG_LEVELS) {
            problems += "LOG_LEVEL: must be one of ${LOG_LEVELS.joinToString(", ")}"
        }
        // DEBUG печатает SQL Exposed с параметрами (PII/секреты) — запрещён в PROD.
        if (config.environment == AppEnvironment.PROD &&
            config.observability.logLevel in setOf("TRACE", "DEBUG")
        ) {
            problems += "LOG_LEVEL: TRACE/DEBUG are forbidden in PROD"
        }
        if (config.observability.metricsEnabled) {
            val token = config.observability.metricsToken
            if (token.isNullOrBlank() || token.length < 16) {
                problems += "METRICS_TOKEN: required (at least 16 chars) when METRICS_ENABLED=true"
            }
        }
    }

    private fun validateTrustedProxies(config: AppConfig, problems: MutableList<String>) {
        config.http.trustedProxies.forEach { entry ->
            if (IpAddresses.parseCidr(entry) == null) {
                problems += "TRUSTED_PROXIES: '$entry' is not an IP address or CIDR"
            }
        }
        // Без доверенного прокси реальный IP клиента неизвестен, а IP-лимиты
        // становятся глобальными (SEC-05/SEC-14).
        if (config.environment == AppEnvironment.PROD && config.http.trustedProxies.isEmpty()) {
            problems += "TRUSTED_PROXIES: required in PROD (TLS-terminating reverse proxy)"
        }
    }
}
