# iOS-приложение SyncClip

Xcode-проект генерируется из `project.yml` через [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(сам `*.xcodeproj` в `.gitignore`):

```bash
cd app/iosApp
xcodegen generate
open iosApp.xcodeproj
```

Перед сборкой Xcode сам вызывает
`./gradlew :app:iosApp:embedAndSignAppleFrameworkForXcode` и подключает
статический framework `ComposeApp` с общим Compose UI.

**Share Extension** собирается отдельно и Kotlin-фреймворк не линкует: текст
шифруется CryptoKit-ом и уходит по WebSocket сразу из расширения. Ключ E2EE
читается из общего Keychain (`keychain-access-groups` в `project.yml`),
access-токен/`deviceId`/адрес API приложение зеркалит в App Group
(`ShareBridge`). Если сессии или сети нет — текст ждёт следующего запуска.
Детали и вектор совместимости — `ShareExtension/README.md`, решение —
`docs/ADR/ADR-012-ios-share-immediate-send.md`.

Сборка только расширения:

```bash
xcodebuild -project iosApp.xcodeproj -target ShareExtension \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Сборка из терминала:

```bash
cd app/iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug \
  CODE_SIGNING_ALLOWED=NO build
```
