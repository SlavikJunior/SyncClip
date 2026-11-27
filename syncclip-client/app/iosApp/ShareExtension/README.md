# iOS Share Extension — немедленная отправка

Расширение принимает текст из системного Share Sheet и **сразу** отправляет его
на сервер тем же E2EE-путём, что основное приложение. Если сессии, ключа или
сети нет — текст кладётся в App Group и уходит при следующем запуске приложения.

## Что общего с приложением

| Данные | Где живут | Кто пишет |
|--------|-----------|-----------|
| E2EE-ключ (32 Б) | iOS Keychain, `service=io.github.slavikjunior.syncclip.e2ee`, `account=e2ee-key` | основное приложение при разблокировке |
| `access_token`, `user_id` | App Group `UserDefaults`, ключи `share.*` | `ShareBridge` в `:core:datastore` на каждой записи сессии |
| `device_id` | App Group, `share.device_id` | `DeviceIdentityPreferences` |
| адрес API | App Group, `share.api_base_url` | `networkModule` (`publishSharedEndpoint`) |
| `pendingShares` | App Group | расширение: не удалось отправить |
| `sentShares` | App Group | расширение: отправлено, приложение добавит в историю |

Ключ доступен расширению потому, что оба таргета объявляют одну и ту же группу
доступа Keychain (`keychain-access-groups` = application-identifier приложения).
Refresh-токен расширению **не** передаётся: ротацию пары выполняет только
приложение, иначе `refresh_token_reused` развёл бы сессии.

`ShareBridge` живёт в `:core:datastore` (`ShareBridge.ios.kt`), на Android/JVM —
no-op.

## Протокол

`SharePayload.swift` повторяет формат `:shared:crypto` и
`:feature:sync:impl`:

- `encryptedPayload = base64(IV (12) ‖ ciphertext ‖ tag (16))`,
  AES-256-GCM без AAD;
- `payloadHash = sha256(utf8(text))` в нижнем hex;
- кадр `SYNC_EVENT` (`type`, `eventId`, `originDeviceId`, `targetDeviceIds=[]`,
  `encryptedPayload`, `payloadHash`, `timestamp`);
- WebSocket `ws(s)://<host>/v1/sync?deviceId=<id>` с `Authorization: Bearer`;
- после событий расширение шлёт `PING` и ждёт `PONG`: сервер обрабатывает кадры
  одной сессии последовательно, поэтому `PONG` подтверждает приём `SYNC_EVENT`.
  `ERROR` считается отказом.

### Вектор совместимости

Ключ `00 01 … 1F`, IV — 12 нулей, текст `share-vector`:

```
encryptedPayload = AAAAAAAAAAAAAAAAfTUrNAB9dhr3MZHbLP7X/6yAFH5pRTY1gNB9w==
payloadHash      = 28b49c73818b0b7fdcae59de64c4df11f89f186df42ae27809fe7c481bc595eb
```

Тест Kotlin: `AesGcmShareVectorTest` (`:shared:crypto`). При изменении
Swift-кода вектор нужно сверить тем же значением (`swiftc`-харнесс или
Xcode-консоль).

## Ограничения

- access-токен живёт час: если он истёк, расширение не обновляет пару (это
  сделало бы refresh-семью несовместимой с приложением), а кладёт текст в
  `pendingShares` — следующий запуск приложения отправит его после refresh;
- повторная отправка при неоднозначном таймауте возможна; получатель
  дедуплицирует по `eventId` только одинаковые события, поэтому в редком случае
  возможен дубль содержимого;
- контейнер `pendingShares`/`sentShares` ограничен 50 записями.

## Сборка

Расширение не линкует Kotlin-фреймворк и не требует Compose. XcodeGen
подхватывает Swift-файлы из `ShareExtension/` и entitlements из `project.yml`:

```bash
cd app/iosApp && xcodegen generate
xcodebuild -project iosApp.xcodeproj -target ShareExtension \
  -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO
```
