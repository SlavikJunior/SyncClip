# SyncClip Backend

Серверная часть SyncClip: Kotlin 2.4 + Ktor 3.6 (Netty), PostgreSQL 16, Exposed 1.6,
Koin. JVM-only, независимая Gradle-сборка (профиль `kmp-app` клиента на сервер не
распространяется).

Реализация по дизайну [`docs/backend/BACKEND_DESIGN.md`](../../docs/backend/BACKEND_DESIGN.md):
REST из `docs/API_SPEC.yaml`, WebSocket из `docs/WEBSOCKET_PROTOCOL.md`, схема БД —
канонический `docs/backend/DB_SCHEMA.sql` (копия в `server/core/database/src/main/resources/db/migration/V1__init.sql`).

## Модули

```text
:server:contracts            DTO, ErrorCode, WS-сообщения, порты, валидаторы (только kotlinx.serialization)
:server:core:config          AppConfig, env-загрузчик, fail-fast проверки
:server:core:security        PBKDF2-хешер, JWT, lockout-политика, token bucket
:server:core:database        Hikari + Exposed, мигратор V1, репозитории (top-level tx-функции)
:server:core:push            PushSender (noop / FCM HTTP v1), PushNotifier
:server:feature:auth         register/login/refresh/logout, canary+attempt, guest invite/exchange
:server:feature:devices      список/переименование/удаление + broadcast DEVICE_UPDATED/REMOVED
:server:feature:sync         очередь (30/48ч, вытеснение), GET /sync/pending, WS relay, реестр сессий
:server:feature:notifications FCM-токены, производная лента /notifications/unread
:server:app                  Main, плагины Ktor, Koin, health, maintenance jobs, i18n, JSON-логи
```

Правила графа проверяются задачей `checkModuleDependencies` (R-1…R-3: `feature ↛ feature`,
`core ↛ feature/app`, `contracts` без проектных зависимостей).

## Запуск

```bash
# PostgreSQL
docker run -d --name syncclip-db \
  -e POSTGRES_PASSWORD=syncclip -e POSTGRES_DB=syncclip \
  -p 5432:5432 postgres:16

# сервер (миграции применяются при старте)
DB_URL=jdbc:postgresql://localhost:5432/syncclip \
DB_USER=postgres DB_PASSWORD=syncclip \
JWT_SECRET='dev-secret-dev-secret-dev-secret-1234' \
PUSH_PROVIDER=noop \
./gradlew :server:app:run

# или docker compose (APP_ENV=prod; нужны DB_PASSWORD, JWT_SECRET, TRUSTED_PROXIES)
DB_PASSWORD=... JWT_SECRET=... TRUSTED_PROXIES=172.18.0.2/32 docker compose up --build
```

Проверки: `curl localhost:8080/health/live`, `/health/ready`.

## Конфигурация

Все параметры — env-переменные (дизайн §5.2). HOCON-файл не читается: env и дефолты
совпадают с §5.1/§5.2, приоритет env тривиален. Секреты (`JWT_SECRET`, `DB_PASSWORD`,
FCM JSON) — только env.

Парсинг строгий: неизвестное значение (`SERVER_PORT=abc`, `LOG_JSON=maybe`) и
опасные числа (`RATE_LIMIT_*=0`, `QUEUE_CLEANUP_INTERVAL_SECONDS=0`) останавливают
старт с именем переменной (без значения). `LOG_LEVEL` — только
`TRACE|DEBUG|INFO|WARN|ERROR`; `APP_ENV=prod` требует непустой `TRUSTED_PROXIES`
и запрещает `TRACE/DEBUG`.

Ключевые переменные: `APP_ENV`, `SERVER_PORT`, `DB_URL/DB_USER/DB_PASSWORD`,
`DB_STATEMENT_TIMEOUT_MS`, `JWT_SECRET`, `DEVICE_LIMIT_FREE`, `OFFLINE_QUEUE_LIMIT`,
`OFFLINE_QUEUE_TTL_HOURS`, `GUEST_SESSION_TTL_SECONDS`, `GUEST_INVITE_TTL_SECONDS`,
`PUSH_PROVIDER`, `FCM_CREDENTIALS_PATH`, `RATE_LIMIT_*`, `WS_*`, `LOG_LEVEL`, `LOG_JSON`,
`METRICS_ENABLED`, `METRICS_TOKEN`, `AUTH_PROVIDER`, `FIREBASE_PROJECT_ID`,
`FIREBASE_JWKS_URL`, `FIREBASE_REQUIRE_VERIFIED_EMAIL`.

`AUTH_PROVIDER`:
- `password` — только `/auth/register|login`;
- `firebase` — только `POST /auth/firebase` (Firebase ID token, RS256, публичные
  JWKS Google) → обычные access/refresh JWT; password-маршруты отвечают 404,
  поэтому email enumeration через `409` исключён;
- `both` (дефолт compose/.env.example) — оба пути. Mobile входит через Firebase,
  Desktop — серверным паролем; Firebase-вход привязывается по verified email к
  существующему аккаунту, поэтому устройства видят один аккаунт.

`FIREBASE_PROJECT_ID` обязателен для `firebase`/`both`, `FIREBASE_JWKS_URL`
в PROD — только https.

Метрики (OPS-07): `METRICS_ENABLED=true` включает `GET /metrics` в формате Prometheus;
обязательный `METRICS_TOKEN` (≥16 символов) передаётся как `Authorization: Bearer …`
или `X-Metrics-Token`. Эндпоинт отдаёт WS-соединения, глубину offline queue, счётчики
relay/auth/push и latency-relay. Без токена — `401`.

`TRUSTED_PROXIES` — список адресов/CIDR reverse proxy. `X-Forwarded-For` читается
только если TCP-соединение пришло от одного из них; цепочка разбирается справа
налево, левые (клиентские) значения не принимаются на веру. Без списка все
клиенты считаются по socket-адресу (за прокси это IP прокси → лимиты глобальные).

Лимиты WS — на устройство, а не на соединение: `WS_MAX_CONNECTIONS_PER_DEVICE`
(по умолчанию 3), `WS_MAX_CONNECTIONS_PER_USER` (8), `WS_MAX_INITIAL_PENDING_BYTES`
(4 MiB, остаток клиент добирает через `GET /sync/pending`).


## Тесты

```bash
# unit (без БД): lockout, JWT, хешер, лимитеры, валидаторы, сериализация, i18n, логи
./gradlew :server:contracts:test :server:core:security:test :server:app:test --tests "*Test"

# интеграционные (нужен PostgreSQL 16)
docker run -d --name syncclip-test-db -e POSTGRES_PASSWORD=test -e POSTGRES_DB=syncclip_test -p 55433:5432 postgres:16
TEST_DB_URL=jdbc:postgresql://localhost:55433/syncclip_test \
TEST_DB_USER=postgres TEST_DB_PASSWORD=test \
./gradlew test
```

Покрыто: auth-флоу (регистрация, вход, refresh-ротация, logout), canary + lockout 3/5м,
гостевой invite/exchange, лимит 3 устройств, переименование/удаление, лента
уведомлений, WS relay на реальном Netty, pending-first-frame, ACK, анти-спуфинг,
лимит размера payload, rate limit, i18n ru/en, health. Регрессионные тесты по
аудиту 2026-11-24 (`AuditRegressionTest`, `SyncServerIntegrationTest`) покрывают
гостевые привилегии, refresh-гонку, маппинг 4xx, fan-out pending, пагинацию,
login-lockout, лимит WS-соединений и per-device rate limit.

Интеграционные тесты **обязательны** в CI (`.github/workflows/backend.yml`,
PostgreSQL service): шаг падает, если появились skipped-тесты. Локально:
`make check-integration` (проверяет, что `TEST_DB_URL` задан).


Без `TEST_DB_URL` интеграционные тесты пропускаются (JUnit assumption), unit-тесты
выполняются всегда.

E2E против запущенного сервера: `scripts/e2e_smoke.py` (нужен `websockets`).

## Осознанные отклонения от дизайна

| Пункт дизайна | Реализация | Причина |
|---|---|---|
| Flyway 13 | собственный идемпотентный `MigrationRunner` (`schema_history`, checksum, advisory lock, statement-splitter) | зависимость помечена «требует апрува»; поведение для V1 эквивалентно |
| Argon2id (`argon2-jvm`) | `Pbkdf2PasswordHasher` (PBKDF2-HMAC-SHA256, 600k), интерфейс `PasswordHasher` | нет апрува + JNA-нативная библиотека не гарантирована на darwin-arm64; замена — одна строка в DI, `password_algo` в БД уже различает алгоритм |
| Firebase Admin SDK | `FcmPushSender` на FCM HTTP v1 (JDK HttpClient, RS256) | SDK тянет gRPC/Netty и конфликтует с Netty Ktor (предупреждение Приложения A) |
| HOCON `application.conf` | env-only загрузчик | лишний артефакт в стеке; имена переменных и дефолты сохранены |
| Micrometer/Prometheus | собственный `MetricsRegistry` + защищённый `GET /metrics`; алерты и scrape-конфиг в `deploy/prometheus/` | OPS-07; Micrometer не нужен для counters/summaries/gauges MVP |
| detekt/ktlint | подключены (`dev.detekt` 2.0 alpha, ktlint 1.8) с baseline на модуль; новые находки валят `check` | OPS-06; alpha detekt — единственная версия, работающая на JDK 25 (1.23.8 падает на разборе версии JVM) |
| SCA | `gradle.lockfile` во всех модулях + OSV-Scanner и Trivy image-scan job в CI | OPS-06: зависимости и образ проверяются до merge |
| ARCH-01 (R-8) | `DeviceAuthenticator` в `core:auth`; `AuthService` разделён на `SessionService`/`SessionTokenStore`; repository-функции `internal`, фичи используют фасад `Repositories.*` | публичных top-level repository-функций больше нет; `checkSourceLayers` запрещает обход фасада |
| ARCH-04 | ArchUnit-тесты слоёв + контрактный тест по `docs/API_SPEC.yaml` + `checkSourceLayers` | ArchUnit проверяет байткод, contract test — пути и lockout-контракт |
| Coverage gate | `jacocoCoverageVerification` ≥75% line (агрегированный отчёт всех модулей) в `check` | NFR-10; текущее агрегированное покрытие ~82% |
| Testcontainers | `TEST_DB_URL` + JUnit assumption | как в §14.2: «начать с переменной окружения» |
| `sessionGateway` в `feature:auth` | порт из `contracts`, реализация `SessionRegistry` | R-1: feature ↛ feature |

## Решения по находкам аудита, требующим продуктового выбора

| Находка | Решение | Обоснование |
|---|---|---|
| SEC-09 device-ID squatting | `deviceId` уникален в паре `(user_id, id)` (V4); один UUID может принадлежать разным аккаунтам | Аудит: глобальный PK позволял занять чужой deviceId. Sessions/queue/FCM/watermark адресуются парой (userId, deviceId) |
| SEC-10 email enumeration | additive Firebase Auth: `AUTH_PROVIDER=firebase`, `POST /auth/firebase`, V5 `users.firebase_uid`; password register/login в этом режиме не регистрируются | В firebase-режиме `409`/`403` enumeration недоступны; для реального проекта нужны `FIREBASE_PROJECT_ID` и клиенты на Firebase SDK |
| `deviceModel` (LOW-08) | добавлено поле `userAgent`; `deviceModel` берётся из `X-Device-Model` (или имени устройства для master-password), а не из UA | Аддитивно, старые клиенты не ломаются |
| LOW-10 guest broadcast | разрешён: гость может отправить событие всем устройствам аккаунта | Продуктовое решение пользователя; лента и push для гостя по-прежнему запрещены |
| SEC-14 TLS/HSTS | TLS на прокси; HSTS отдаётся приложением в `APP_ENV=prod` | Приложение слушает HTTP за доверенным прокси по дизайну §12.7 |


Дополнительно: **WS-тесты на Ktor test-host не используются** — test engine не
диспатчит корутину WS-хендлера на другой диспетчер, из-за чего `withContext` в
транзакции зависает. WS проверяется на реальном Netty (`SyncServerIntegrationTest`)
и скриптом `scripts/e2e_smoke.py`; продовый код от этого не менялся.

## Безопасность

- Сервер хранит только шифротекст (`BYTEA`), `payloadHash` и маршрутные метаданные;
  мастер-пароль, ключ E2EE и plaintext не принимаются и не логируются.
- Canary — opaque base64; сервер его не расшифровывает и не сравнивает.
- Refresh-ротация сериализуется семейным advisory lock + `SELECT … FOR UPDATE`:
  один исходный токен не может породить два активных. Reuse вне grace-окна
  отзывает семейство и инкрементирует `token_version`.
- Гость: только sync; management, `logout?allDevices=true`, лента уведомлений и
  FCM-регистрация → `403 forbidden_for_guest`. Push уходит только на постоянные
  устройства.
- Логи JSON с редакцией секретов (`password`, `refreshToken`, `encryptedPayload`,
  JSON-формы и `Bearer …`); Exposed принудительно на WARN (SQL с параметрами не
  печатается).
- `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, HSTS в PROD;
  `X-Forwarded-For` принимается только от адресов из `TRUSTED_PROXIES`.
- IP валидируется как литерал (`InetAddress`), `email_hash` в аудите —
  HMAC-SHA256 с pepper'ом, выведенным из `JWT_SECRET`.
- `deviceId` уникален только внутри аккаунта (V4): занятие чужого deviceId
  не блокирует регистрацию/вход; WS-сессии, очередь, FCM и ACK-watermark
  адресуются парой `(userId, deviceId)`.
- Rate limit по IP/userId/deviceId; refresh/logout имеют отдельные лимиты;
  WS — token bucket 100 SYNC_EVENT и 200 ACK в минуту **на устройство**
  (переживает реконнект), число соединений ограничено.

## Наблюдаемость

- `GET /metrics` (Prometheus text): `syncclip_ws_active_connections`,
  `syncclip_offline_queue_depth`, `syncclip_ws_connections_total`,
  `syncclip_ws_rate_limited_total{type}`, `syncclip_relay_events_total`,
  `syncclip_relay_latency_seconds` (summary), `syncclip_auth_failures_total{code}`,
  `syncclip_push_delivered_total`, `syncclip_push_invalid_tokens_total`,
  `syncclip_push_retryable_total`.
- Включается `METRICS_ENABLED=true`, защищён `METRICS_TOKEN`; при выключенном
  флаге маршрут отсутствует (404).
- Покрытие измеряется JaCoCo (агрегированный `jacocoAggregatedReport`, XML+HTML),
  gate `jacocoCoverageVerification` ≥75% line входит в `check`; CI публикует HTML.
- Статический анализ: `detekt` (2.0, baseline на модуль) и `ktlint` (1.8,
  baseline) входят в `check`; новые находки валят сборку. Baseline обновляется
  `detektBaseline`/`ktlintGenerateBaseline`.
- SCA: dependency locking (`gradle.lockfile`) + OSV-Scanner job;
  Trivy image-scan job собирает Docker-образ и валит HIGH/CRITICAL.
  Обновление lock-файлов — `./gradlew lockAllDependencies --write-locks`.
- Алерты и scrape-конфиг Prometheus — `deploy/prometheus/alerts.yml` и
  `deploy/prometheus/scrape.yml`.
- Source-layer gate `checkSourceLayers` проверяет чистоту `contracts`/`core`,
  отсутствие Ktor в `feature/domain`, запрет `GlobalScope`/`!!`/`printStackTrace`
  и использование только фасада `Repositories.*`.
