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

Сборка из терминала:

```bash
cd app/iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug \
  CODE_SIGNING_ALLOWED=NO build
```
