package io.github.slavikjunior.syncclip.server.core.config

/**
 * Загрузчик конфигурации: env → типизированный [AppConfig] → fail-fast валидация.
 *
 * Отклонение от §5.1 дизайна: HOCON-файл не читается (не тянем отдельный
 * артефакт конфигурации), env-переменные и дефолты сохранены один в один,
 * приоритет env над файлом становится тривиальным. См. отчёт о реализации.
 *
 * Строгость (OPS-01): любое нечитаемое значение — ошибка старта с именем
 * переменной. Молчаливая подстановка дефолта запрещена: опечатка в env
 * не должна менять поведение сервера незаметно.
 */
object ConfigLoader {

    fun load(env: Map<String, String> = System.getenv()): AppConfig {
        val source = EnvSource(env)
        val config = AppConfig(
            environment = AppEnvironment.fromWire(source.raw("APP_ENV")),
            server = ServerConfig(
                host = source.string("SERVER_HOST", "0.0.0.0"),
                port = source.int("SERVER_PORT", 8080),
                gracefulShutdownMillis = source.long("GRACEFUL_SHUTDOWN_MILLIS", 25_000),
                requestBodyLimitBytes = source.long("REQUEST_BODY_LIMIT_BYTES", 1_048_576),
            ),
            database = DatabaseConfig(
                url = source.raw("DB_URL") ?: "",
                user = source.raw("DB_USER") ?: "",
                password = source.raw("DB_PASSWORD") ?: "",
                maxPoolSize = source.int("DB_POOL_SIZE", 10),
                connectionTimeoutMs = source.long("DB_CONNECTION_TIMEOUT_MS", 5_000),
                statementTimeoutMs = source.long("DB_STATEMENT_TIMEOUT_MS", 10_000),
                migrateOnStart = source.boolean("DB_MIGRATE_ON_START", true),
            ),
            jwt = JwtConfig(
                issuer = source.string("JWT_ISSUER", "syncclip"),
                audience = source.string("JWT_AUDIENCE", "syncclip-client"),
                secret = source.raw("JWT_SECRET") ?: "",
                accessTtlSeconds = source.long("JWT_ACCESS_TTL_SECONDS", 3_600),
                refreshTtlDays = source.long("JWT_REFRESH_TTL_DAYS", 30),
                clockSkewSeconds = source.long("JWT_CLOCK_SKEW_SECONDS", 60),
            ),
            security = SecurityConfig(
                password = PasswordConfig(
                    // OWASP 2023 для PBKDF2-HMAC-SHA256 — 600k (LOW-01).
                    iterations = source.int("PASSWORD_HASH_ITERATIONS", 600_000),
                    saltLength = source.int("PASSWORD_SALT_LENGTH", 16),
                    keyLength = source.int("PASSWORD_KEY_LENGTH", 32),
                ),
                lockout = LockoutConfig(
                    groupSize = source.int("LOCKOUT_GROUP_SIZE", 3),
                    shortLockMinutes = source.long("LOCKOUT_SHORT_MINUTES", 5),
                    longLockHours = source.long("LOCKOUT_LONG_HOURS", 2),
                ),
                guest = GuestConfig(
                    sessionTtlSeconds = source.long("GUEST_SESSION_TTL_SECONDS", 3_600),
                    inviteTtlSeconds = source.long("GUEST_INVITE_TTL_SECONDS", 600),
                ),
                deviceLimitFree = source.int("DEVICE_LIMIT_FREE", 3),
                // CG-27: login-lockout по умолчанию выключен до апрува.
                loginLockout = LoginLockoutConfig(
                    enabled = source.boolean("LOGIN_LOCKOUT_ENABLED", false),
                    failures = source.int("LOGIN_LOCKOUT_FAILURES", 10),
                    windowMinutes = source.long("LOGIN_LOCKOUT_WINDOW_MINUTES", 15),
                    lockMinutes = source.long("LOGIN_LOCKOUT_MINUTES", 15),
                ),
            ),
            queue = QueueConfig(
                limitPerDevice = source.int("OFFLINE_QUEUE_LIMIT", 30),
                ttlHours = source.long("OFFLINE_QUEUE_TTL_HOURS", 48),
                cleanupIntervalSeconds = source.long("QUEUE_CLEANUP_INTERVAL_SECONDS", 60),
            ),
            auth = AuthConfig(
                provider = source.string("AUTH_PROVIDER", "password").lowercase(),
                firebaseProjectId = source.raw("FIREBASE_PROJECT_ID"),
                firebaseJwksUrl = source.string(
                    "FIREBASE_JWKS_URL",
                    "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com",
                ),
                firebaseRequireVerifiedEmail = source.boolean("FIREBASE_REQUIRE_VERIFIED_EMAIL", true),
            ),
            push = PushConfig(
                provider = source.string("PUSH_PROVIDER", "noop").lowercase(),
                credentialsPath = source.raw("FCM_CREDENTIALS_PATH"),
                projectId = source.raw("FCM_PROJECT_ID"),
            ),
            http = HttpConfig(
                trustedProxies = source.list("TRUSTED_PROXIES"),
            ),
            observability = ObservabilityConfig(
                logLevel = source.string("LOG_LEVEL", "INFO").uppercase(),
                jsonLogs = source.boolean("LOG_JSON", true),
                metricsEnabled = source.boolean("METRICS_ENABLED", false),
                metricsToken = source.raw("METRICS_TOKEN"),
            ),
            ws = WebSocketConfig(
                allowQueryToken = source.boolean("WS_ALLOW_QUERY_TOKEN", false),
                outboundCapacity = source.int("WS_OUTBOUND_CAPACITY", 2),
                maxInflightBytes = source.long("WS_MAX_INFLIGHT_BYTES", 2_097_152),
                maxInitialPendingBytes = source.long("WS_MAX_INITIAL_PENDING_BYTES", 4_194_304),
                maxConnectionsPerUser = source.int("WS_MAX_CONNECTIONS_PER_USER", 8),
                maxConnectionsPerDevice = source.int("WS_MAX_CONNECTIONS_PER_DEVICE", 3),
                pingPeriodSeconds = source.long("WS_PING_PERIOD_SECONDS", 30),
                timeoutSeconds = source.long("WS_TIMEOUT_SECONDS", 15),
                maxFrameBytes = source.long("WS_MAX_FRAME_BYTES", 1_048_576),
            ),
            limits = LimitsConfig(
                enabled = source.boolean("RATE_LIMIT_ENABLED", true),
                loginPerMinute = source.int("RATE_LIMIT_LOGIN_PER_MINUTE", 5),
                registerPerMinute = source.int("RATE_LIMIT_REGISTER_PER_MINUTE", 5),
                refreshPerMinute = source.int("RATE_LIMIT_REFRESH_PER_MINUTE", 60),
                logoutPerMinute = source.int("RATE_LIMIT_LOGOUT_PER_MINUTE", 30),
                canaryPerMinute = source.int("RATE_LIMIT_CANARY_PER_MINUTE", 10),
                canaryAttemptPerMinute = source.int("RATE_LIMIT_CANARY_ATTEMPT_PER_MINUTE", 10),
                guestInvitePerHour = source.int("RATE_LIMIT_GUEST_INVITE_PER_HOUR", 10),
                guestExchangePerMinute = source.int("RATE_LIMIT_GUEST_EXCHANGE_PER_MINUTE", 10),
                defaultPerMinute = source.int("RATE_LIMIT_DEFAULT_PER_MINUTE", 100),
                pendingPerMinute = source.int("RATE_LIMIT_PENDING_PER_MINUTE", 30),
                syncEventsPerMinute = source.int("RATE_LIMIT_SYNC_EVENTS_PER_MINUTE", 100),
                acksPerMinute = source.int("RATE_LIMIT_ACKS_PER_MINUTE", 200),
                pingPerMinute = source.int("RATE_LIMIT_PING_PER_MINUTE", 10),
            ),
        )
        ConfigValidation.validate(config)
        return config
    }

    private class EnvSource(private val env: Map<String, String>) {
        fun raw(key: String): String? = env[key]?.trim()?.takeIf { it.isNotEmpty() }

        fun string(key: String, default: String): String = raw(key) ?: default

        fun int(key: String, default: Int): Int {
            val raw = raw(key) ?: return default
            return raw.toIntOrNull() ?: throw invalid(key, "must be an integer")
        }

        fun long(key: String, default: Long): Long {
            val raw = raw(key) ?: return default
            return raw.toLongOrNull() ?: throw invalid(key, "must be an integer")
        }

        fun boolean(key: String, default: Boolean): Boolean =
            when (raw(key)?.lowercase()) {
                null -> default
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> throw invalid(key, "must be a boolean (true/false)")
            }

        fun list(key: String): List<String> =
            raw(key)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        private fun invalid(key: String, rule: String) =
            ConfigValidationException(listOf("$key: $rule"))
    }
}
