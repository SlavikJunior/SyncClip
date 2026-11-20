# SyncClip

Кроссплатформенное приложение для мгновенной и безопасной синхронизации буфера обмена между личными устройствами с E2EE-шифрованием.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/Kotlin_Multiplatform-2.0-blue.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose](https://img.shields.io/badge/Compose_Multiplatform-1.7-green.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

---

## 🎯 Обзор

SyncClip автоматически синхронизирует текстовый буфер обмена между вашими устройствами:
- 📱 **Android** — Share Sheet, Quick Settings Tile, автоматический захват
- 🍎 **iOS** — Share Extension, автоматический захват при активном приложении
- 💻 **Desktop** (Windows, macOS, Linux) — фоновый мониторинг, системный трей, глобальный хоткей

**Ключевые особенности:**
- 🔒 **E2EE (AES-256-GCM)** — сервер не видит содержимое буфера
- ⚡ **LAN P2P** — прямая передача в локальной сети (< 200 мс)
- ☁️ **WAN Relay** — автоматический fallback через сервер при отсутствии прямого соединения
- 📝 **Локальная история** — до 200 записей с закреплением и поиском
- 🔕 **Offline режим** — остановка синхронизации одним кликом

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

## 🔒 Безопасность

### E2EE Flow

1. **Регистрация:**
   ```
   User → Мастер-пароль → PBKDF2(100k iterations) → AES-256 ключ
   Ключ сохраняется в Keychain/Keystore
   Canary = AES-GCM("syncclip-canary-" + UUID, ключ) → сервер
   ```

2. **Отправка пакета:**
   ```
   Текст → AES-GCM(текст, ключ, randomIV) → Base64(IV + ciphertext + authTag)
   Пакет {eventId, encryptedPayload, payloadHash} → LAN P2P / Сервер
   ```

3. **Получение пакета:**
   ```
   Пакет → AES-GCM-Decrypt(encryptedPayload, ключ) → Текст
   Проверка: SHA256(текст) == payloadHash
   ```

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
| **UI** | Compose Multiplatform 1.7+                          |
| **Архитектура** | Clean Architecture + MVI                            |
| **DI** | Koin                                                |
| **Сеть** | Ktor Client (HTTP/WebSocket)                        |
| **БД** | Room KMP 3.0+                                       |
| **Настройки** | Jetpack DataStore (Preferences)                     |
| **Кэш** | io.github.slavikjunior.kache                        |
| **Криптография** | AES-256-GCM (javax.crypto / CryptoKit)              |
| **LAN Discovery** | mDNS/NSD (Android NsdManager, Apple Bonjour, JmDNS) |

### Сервер (syncclip-backend)

| Категория | Технология |
|-----------|---------|
| **Язык** | Kotlin 2.4.21+ |
| **Фреймворк** | Ktor 3.0+ |
| **БД** | PostgreSQL 16+ |
| **ORM** | Exposed |
| **Авторизация** | JWT (ktor-auth-jwt) |
| **Push** | Firebase Cloud Messaging (Admin SDK) |
| **Мониторинг** | Micrometer + Prometheus |

---

## 📦 Структура репозиториев

Проект разделён на **два независимых репозитория**:

### syncclip-client

```
syncclip-client/
├── :shared                    # Общий код для всех платформ
│   ├── :crypto               # AES-GCM, PBKDF2, Canary
│   ├── :model                # Data classes (ClipboardEvent, SyncPacket, Device)
│   ├── :network              # Ktor Client, LAN Discovery (expect/actual)
│   └── :util                 # UUID, timestamp, hash utils
├── :core                      # Инфраструктурный слой
│   ├── :database             # Room KMP (DAO, Entities, Migrations)
│   ├── :datastore            # DataStore (Settings)
│   ├── :cache                # Kache wrapper
│   └── :di                   # Koin modules
├── :feature                   # Feature-модули (MVI)
│   ├── :auth                 # Login, Register, MasterPassword
│   ├── :sync                 # SyncCoordinator, TransportUnion
│   ├── :history              # HistoryList, Search, Pin/Unpin
│   └── :settings             # Settings, Limits, Online/Offline toggle
└── :app                       # Платформенные точки входа
    ├── :androidApp           # MainActivity, ShareActivity, QuickSettingsTile
    ├── :iosApp               # SwiftUI wrapper, Share Extension
    └── :desktopApp           # System Tray, Global Hotkey, Clipboard Monitor
```

### syncclip-backend

```
syncclip-backend/
├── src/main/kotlin/
│   ├── Application.kt        # Ktor entry point
│   ├── routes/
│   │   ├── AuthRoutes.kt     # POST /auth/register, /auth/login
│   │   ├── SyncRoutes.kt     # GET /sync/pending
│   │   └── WebSocketRoute.kt # WebSocket /sync
│   ├── handlers/
│   │   └── WebSocketHandler.kt
│   ├── repositories/
│   │   ├── UserRepository.kt
│   │   ├── DeviceRepository.kt
│   │   └── OfflineQueueRepository.kt
│   └── services/
│       └── FCMService.kt
└── src/main/resources/
    └── application.conf
```

---

## 🎯 Roadmap

### MVP (Must Have) — текущий этап ✅

- [x] Документация (PASSPORT.md, ARCHITECTURE.md, ADR)
- [ ] Клиент: авторизация (Email + Password, JWT)
- [ ] Клиент: E2EE (мастер-пароль, canary-валидация)
- [ ] Клиент: гибридная синхронизация (LAN P2P + Ktor Relay)
- [ ] Клиент: локальная история (Room KMP, лимиты, вытеснение)
- [ ] Сервер: REST API (auth, devices, pending packets)
- [ ] Сервер: WebSocket Relay + офлайн-очередь
- [ ] Сервер: FCM-уведомления (неудачные попытки входа)
- [ ] Desktop: системный трей, автозапуск, глобальный хоткей
- [ ] Android: Share Sheet, Quick Settings Tile
- [ ] iOS: Share Extension

### Phase 2 (Should Have)

- [ ] Пауза синхронизации на 15 мин / 1 час
- [ ] Поиск и фильтр в истории
- [ ] Unit-тесты (80%+ coverage)
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

### Запуск клиента

```bash
# Android
./gradlew :app:androidApp:assembleDebug
./gradlew :app:androidApp:installDebug

# iOS (через Xcode)
open iosApp/SyncClip.xcodeproj

# Desktop
./gradlew :app:desktopApp:run
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
