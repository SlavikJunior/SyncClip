# SyncClip

Кроссплатформенное приложение для мгновенной и безопасной синхронизации буфера обмена между личными устройствами с E2EE-шифрованием.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-blue.svg)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/Kotlin_Multiplatform-2.4.21-blue.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose](https://img.shields.io/badge/Compose_Multiplatform-1.12.2-green.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

---

## 🎯 Обзор

SyncClip автоматически синхронизирует текстовый буфер обмена между вашими устройствами:
- 📱 **Android** — автоматический захват, Share Sheet, Quick Settings Tile
- 🍎 **iOS** — автоматический захват при активном приложении, Share Extension с немедленной E2EE-отправкой (fallback — очередь в App Group)
- 💻 **Desktop** (Windows, macOS, Linux) — трей со статусом и паузой, автозапуск, глобальный `Ctrl/Cmd+Shift+V`, захват при скрытом окне

**Ключевые особенности:**
- 🔒 **E2EE (AES-256-GCM)** — сервер не видит содержимое буфера; ключ из мастер-пароля (PBKDF2 100k) и canary
- ☁️ **WAN Relay** — WebSocket `/v1/sync`, офлайн-очередь, ACK, реконнект с backoff
- ⚡ **LAN P2P** — mDNS/NSD, параллельный unicast, ACK ≤ 300 мс, fallback на сервер
- 📝 **Локальная история** — настраиваемые лимиты (5–200 записей, 1 ч–30 дней, 1–100 МБ), закрепление, поиск
- 🔕 **Offline режим** — остановка синхронизации одним кликом
- 🔄 **Авто-refresh токена** — `401` на REST повторяется после ротации пары, WebSocket переподключается отдельно
- 🔐 **Защита мастер-пароля** — остаток попыток и таймер блокировки (3/5 мин → 3/5 мин → 3/2 ч), без запросов во время lockout

---

## 🏗️ Архитектура

### High-Level Overview

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Desktop   │◄───────►│   Android   │◄───────►│     iOS     │
│   Client    │  LAN P2P│   Client    │  LAN P2P│   Client    │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       │         WebSocket (WAN Relay)                 │
       │                       │                       │
       └───────────────────────┼───────────────────────┘
                               │
                        ┌──────▼──────┐
                        │ Ktor Server │
                        │  WebSocket  │
                        │   Relay +   │
                        │   Offline   │
                        │    Queue    │
                        └──────┬──────┘
                               │
                        ┌──────▼──────┐
                        │ PostgreSQL  │
                        └─────────────┘
```

### Клиентская архитектура (Clean Architecture + MVI)

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                    │
│         (Compose UI + ViewModels MVI)                   │
├─────────────────────────────────────────────────────────┤
│                    Domain Layer                         │
│            (Use Cases, Business Logic)                  │
├─────────────────────────────────────────────────────────┤
│                     Data Layer                          │
│  (Repositories, Room, DataStore, Ktor Client, Kache)   │
└─────────────────────────────────────────────────────────┘
```

---

## 📋 Захват буфера

Конвейер реализован в `:core:clipboard` + `:feature:sync:impl`:

```
системный буфер → SystemClipboard (Android listener / iOS changeCount / AWT)
    → ClipboardGateway: пустой текст, 512 КиБ, эхо собственной вставки, дубли
    → debounce 100 мс → Room (история) → исходящий поток (только в Online)
```

- **Android:** `ClipboardManager.addPrimaryClipChangedListener`; Android 10+ отдаёт
  буфер только приложению с фокусом — фоновый захват не делаем (запрещено
  паспортом, §5.4).
- **iOS:** опрос `UIPasteboard.changeCount` каждые 400 мс; iOS 14+ показывает
  системную плашку при чтении — работает при активном приложении.
- **Desktop:** опрос AWT-буфера каждые 300 мс (`FlavorListener` ненадёжен).
- **Входящий текст** обрабатывает `DefaultClipboardIncomingSink`: дедупликация по
  `eventId`, запрет вставки в течение 500 мс после локального копирования, учёт
  тумблеров Online/авто-вставки; доставка — LAN TCP или WAN WebSocket.

Проверка:

```bash
cd syncclip-client
./gradlew :core:clipboard:jvmTest        # правила + реальный буфер macOS/Windows/Linux
./gradlew :core:clipboard:iosSimulatorArm64Test  # очередь Share Extension (pending/sent)
./gradlew :shared:crypto:jvmTest         # векторы NIST + сверка с javax.crypto
./gradlew :shared:network:jvmTest        # exp из JWT, авто-refresh по 401 (MockEngine)
./gradlew :core:di:jvmTest                # DI-граф без циклов, refresh через SessionRefresher
./gradlew :core:lan:jvmTest              # кадры + TCP loopback; mDNS: SYNCCLIP_MDNS_TEST=1
./gradlew :core:datastore:jvmTest        # ключ 600/hydrate, лимиты истории и диапазоны
./gradlew :feature:auth:impl:jvmTest     # canary, refresh, Desktop email/пароль
./gradlew :feature:history:impl:jvmTest  # история, вытеснение по настройкам
./gradlew :feature:settings:impl:jvmTest # устройства, тема, лимиты истории FR-4.3
./gradlew :app:common:jvmTest            # таймер блокировки мастер-пароля FR-1.4
./gradlew :feature:sync:impl:jvmTest     # конвейер, LAN+WAN транспорт, два клиента
./gradlew :app:desktopApp:test           # глобальный хоткей Ctrl/Cmd+Shift+V
```

E2E против настоящего сервера (нужны Docker и PostgreSQL — см. `docs/DATA_FLOWS.md`, раздел 3.4):

```bash
SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:sync:impl:jvmTest --tests '*BackendRelayE2eTest*'
SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:sync:impl:jvmTest --tests '*MasterPasswordLockoutE2eTest*'
SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:auth:impl:jvmTest --tests '*DesktopPasswordAuthE2eTest*'
SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:auth:impl:jvmTest --tests '*BackendTokenRefreshE2eTest*'
```

---

## 🔒 Безопасность

### E2EE Flow (реализовано)

1. **Разблокировка:**
   ```
   Мастер-пароль → PBKDF2-HMAC-SHA256(salt=userId, 100k) → ключ 32 Б
   → GET /v1/auth/canary → AES-GCM-decrypt → проверка "syncclip-canary-"
   (первое устройство: canary создаётся через POST /v1/auth/canary)
   ```
   Ключ сохраняется в защищённом хранилище: Android Keystore (envelope AES/GCM),
   iOS Keychain, Desktop — файл `~/.syncclip/e2ee.key` с правами `rw-------`.
   Повторный ввод мастер-пароля нужен только на новом устройстве.

2. **Отправка пакета:**
   ```
   Текст → AES-256-GCM → Base64(IV 12 + ciphertext + tag 16) = encryptedPayload
   payloadHash = SHA-256(открытый текст, hex)
   SYNC_EVENT {eventId, originDeviceId, encryptedPayload, payloadHash, timestamp}
   ```

3. **Получение пакета:**
   ```
   decrypt(encryptedPayload) → SHA-256(текст) == payloadHash?
   дедупликация eventId (5 мин) → история → авто-вставка → ACK
   ```

Криптография — `:shared:crypto` на чистом Kotlin (CryptoKit недоступен из
Kotlin/Native, javax.crypto — только JVM/Android). На JVM она сверяется с
`javax.crypto` на 50 случайных пакетах и векторах NIST.

**Что сервер НЕ знает:**
- ❌ Содержимое текста (только зашифрованный payload)
- ❌ Мастер-пароль или ключ E2EE
- ❌ История буфера (хранится только локально)

---

## 🚀 Стек технологий

### Клиент (syncclip-client)

| Категория | Технология                                          |
|-----------|-----------------------------------------------------|
| **Язык** | Kotlin 2.4.21+                                      |
| **UI** | Compose Multiplatform 1.12.2                        |
| **Архитектура** | Clean Architecture + MVI                            |
| **DI** | Koin                                                |
| **Сеть** | Ktor Client (HTTP/WebSocket)                        |
| **БД** | Room KMP 2.8.5                                      |
| **Настройки** | Jetpack DataStore (Preferences)                     |
| **Кэш** | io.github.slavikjunior.kache                        |
| **Авторизация** | Firebase Auth (Email/Password, Google) → JWT сервера; Desktop — серверный email/пароль (`AUTH_PROVIDER=both`) |
| **Push** | Firebase Cloud Messaging (Android, iOS)             |
| **Криптография** | AES-256-GCM + PBKDF2-HMAC-SHA256 + SHA-256 (`:shared:crypto`, чистый Kotlin) |
| **Транспорт** | WAN: Ktor Client WebSockets `/v1/sync`; LAN: TCP-unicast с ACK и fallback на сервер |
| **LAN Discovery** | mDNS/NSD (Android NsdManager, Apple NSNetService, JmDNS) |

### Сервер (syncclip-backend)

| Категория | Технология |
|-----------|---------|
| **Язык** | Kotlin 2.4.21 |
| **Фреймворк** | Ktor 3.6.1 (Netty) |
| **БД** | PostgreSQL 16+ |
| **ORM** | Exposed 1.6.1 |
| **Авторизация** | JWT (ktor-auth-jwt) |
| **Push** | Firebase Cloud Messaging (Admin SDK) |
| **Мониторинг** | Micrometer + Prometheus |

---

## 📦 Структура репозитория

Репозиторий — контейнер с двумя независимыми Gradle-проектами:

```
SyncClip/                  # корень: общие файлы, без Gradle-сборки
├── syncclip-client/       # KMP-клиент (Android, iOS, Desktop)
├── syncclip-backend/      # Ktor-сервер (JVM)
├── docs/                  # общая документация (PASSPORT, ARCHITECTURE, ADR)
├── .github/               # CI (backend.yml)
├── .opencode/             # конфигурация агентов
├── Makefile               # make check — проверки обоих проектов
└── README.md
```

Каждый проект открывается в IDE отдельно и имеет собственные `settings.gradle.kts`,
`gradle/`, `build-logic/`, `local.properties`, `.idea/`, `.gradle/`.

### syncclip-client

```
syncclip-client/
├── :shared                    # Общий код для всех платформ
│   ├── :model                # Модели и маршруты (ClipboardPayload, Device, NavKey)
│   ├── :network              # Единственная точка создания Ktor-клиента (+ WebSocket)
│   └── :crypto               # AES-256-GCM, PBKDF2, SHA-256 (чистый Kotlin + CSPRNG)
├── :core                      # Инфраструктурный слой
│   ├── :clipboard            # Системный буфер: Android/iOS/JVM, эхо, лимит 512 КиБ
│   ├── :lan                  # LAN P2P: mDNS/NSD, TCP-сервер/клиент, кадры
│   ├── :database             # Room KMP (история, отложенные изменения устройств)
│   ├── :datastore            # DataStore (сессия JWT, deviceId, Online/авто-вставка)
│   ├── :firebase             # Firebase Auth + FCM (Android SDK / iOS Swift-мост)
│   ├── :cache                # Kache wrapper
│   ├── :designsystem         # Тема, компоненты Compose
│   ├── :di                   # Koin-модули (composition root)
│   └── :architecture-tests   # Konsist-проверки инвариантов
├── :feature                   # Feature-модули (MVI), у каждой api/impl
│   ├── :auth                 # Вход через Firebase → JWT сервера
│   ├── :sync                 # Захват буфера, статус, дедупликация
│   ├── :history              # История, поиск, pin, удаление
│   └── :settings             # Устройства, тема
└── :app                       # Платформенные точки входа
    ├── :common               # Общий Compose-shell и composition root
    ├── :androidApp           # Application, MainActivity, FCM-сервис
    ├── :iosApp               # SwiftUI-обёртка
    └── :desktopApp           # Compose Desktop main
```

### syncclip-backend

```
syncclip-backend/
├── server/contracts            # DTO, ErrorCode, WS-сообщения, порты
├── server/core/{config,security,database,push}
├── server/feature/{auth,devices,sync,notifications}
└── server/app                  # Main, Ktor-плагины, Koin, health, метрики, jobs
```

---

## 🎯 Roadmap

### MVP (Must Have) — текущий этап

- [x] Документация (PASSPORT.md, ARCHITECTURE.md, ADR)
- [x] Клиент: авторизация через Firebase Auth (Email/Password) → JWT сервера
- [x] Клиент: FCM-токен и регистрация на сервере (`POST /fcm/register`)
- [x] Клиент: захват буфера (Android/iOS/Desktop), защита от эха, лимит 512 КиБ
- [x] Клиент: локальная история (Room KMP, вытеснение, поиск, pin)
- [x] Клиент: тумблеры Online/Offline и авто-вставки (DataStore)
- [x] Клиент: E2EE (мастер-пароль, PBKDF2, canary, AES-256-GCM)
- [x] Клиент: WAN-транспорт (WebSocket `/v1/sync`, ACK, backoff, canary)
- [x] Клиент: LAN P2P (mDNS/NSD + параллельный unicast, ACK ≤ 300 мс, fallback)
- [x] Клиент: Android Share Sheet, Quick Settings Tile, iOS Share Extension (немедленная отправка через общий Keychain + App Group)
- [x] Сервер: REST API (auth, devices, pending packets)
- [x] Сервер: WebSocket Relay + офлайн-очередь
- [x] Сервер: FCM-уведомления (неудачные попытки входа)
- [x] Desktop: системный трей, автозапуск, глобальный хоткей

### Phase 2 (Should Have)

- [ ] Пауза синхронизации на 15 мин / 1 час
- [x] Поиск и фильтр в истории
- [ ] Unit-тесты (80%+ coverage; покрытие не измерялось)
- [ ] E2E-тесты на реальных устройствах

### Phase 3 (Could Have)

- [ ] Синхронизация изображений (скриншотов) с автосжатием
- [ ] QR-код для гостевого режима
- [ ] Premium-подписка (до 10 устройств)

---

## 🧪 Разработка

### Требования

- **JDK:** 21+
- **Kotlin:** 2.4.21
- **Android Studio:** Ladybug | 2024.2.1+
- **Xcode:** 15+ (для iOS)
- **PostgreSQL:** 16+ (для локальной разработки сервера)

### Открытие в Android Studio / IntelliJ IDEA

Корень репозитория открывается как один workspace с двумя **linked Gradle-проектами**:

1. `File → Open` → каталог `SyncClip` (корень).
2. Если проекты не подхватились автоматически: окно **Gradle** → `+` → **Link Gradle Project** → `syncclip-client`, затем `syncclip-backend`.
3. **Sync Project with Gradle Files**.

В дереве появятся оба проекта: `syncclip-client` (Android/iOS/Desktop-приложения, модуль `:app:androidApp`) и `syncclip-backend` (Ktor-сервер, модуль `:server:app`). Настройка хранится в локальном `.idea/` и в git не входит — на новой машине шаги 2–3 нужно повторить.

Готовые конфигурации запуска: `androidApp` (модуль `SyncClip.app.androidApp`), `desktopApp`, `backend`, `iosApp`.

Если дерево проекта выглядит неверно (виден весь домашний каталог, старые модули, ошибки `Module not specified`): закрыть IDE, выполнить `make ide-reset`, открыть корень заново и сделать Sync. Команда удаляет модель проекта (`workspace.xml`, `.iml`, кэш модели IDE), но сохраняет привязку Gradle-проектов (`gradle.xml`) и run-конфигурации.

### Запуск клиента

```bash
cd syncclip-client

# Android
./gradlew :app:androidApp:assembleDebug
./gradlew :app:androidApp:installDebug

# iOS (через Xcode, проект генерируется XcodeGen)
cd app/iosApp && xcodegen generate && open iosApp.xcodeproj

# Desktop: вход по email/паролю через сервер (Firebase на JVM недоступен),
# трей, автозапуск, Ctrl/Cmd+Shift+V.
# Адрес сервера по умолчанию http://localhost:8080; переопределяется
# SYNCCLIP_API_BASE_URL (например, второй инстанс на :8081).
./gradlew :app:desktopApp:run
./gradlew :app:desktopApp:createDistributable  # упакованное приложение
```

### Запуск сервера

```bash
cd syncclip-backend

# Запуск PostgreSQL (Docker)
docker run -d --name syncclip-db \
  -e POSTGRES_PASSWORD=syncclip \
  -e POSTGRES_DB=syncclip \
  -p 5432:5432 postgres:16

# Запуск сервера
./gradlew run
```

---

## 📄 Лицензия

Proprietary — все права защищены. Проект предназначен для личного использования автора.
