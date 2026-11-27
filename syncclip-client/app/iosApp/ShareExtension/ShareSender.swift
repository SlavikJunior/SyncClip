import Foundation
import Security

/// Немедленная отправка текста из Share Extension.
///
/// Расширение не запускает основное приложение и не линкует Kotlin: оно читает
/// общий E2EE-ключ из Keychain (группа доступа приложения выдана расширению в
/// entitlements), access-токен/`deviceId`/адрес сервера — из App Group
/// `UserDefaults`, шифрует текст CryptoKit-ом и отправляет `SYNC_EVENT`
/// по WebSocket. PING после события подтверждает, что сервер обработал кадр
/// (он читает сообщения одной сессии последовательно).
///
/// Если сессии/ключа/сети нет — тексты возвращаются вызывающему, и расширение
/// кладёт их в очередь `pendingShares` для следующего запуска приложения.
enum ShareSender {

    static let appGroup = "group.io.github.slavikjunior.syncclip"
    static let pendingSharesKey = "pendingShares"
    static let sentSharesKey = "sentShares"

    private static let keyUserId = "share.user_id"
    private static let keyAccessToken = "share.access_token"
    private static let keyDeviceId = "share.device_id"
    private static let keyApiBaseURL = "share.api_base_url"

    private static let keychainService = "io.github.slavikjunior.syncclip.e2ee"
    private static let keychainAccount = "e2ee-key"

    private static let timeoutSeconds: UInt64 = 10

    struct Environment {
        let baseURL: String
        let accessToken: String
        let deviceId: String
        let key: Data
    }

    enum SendError: Error {
        case timeout
        case disconnected
        case serverError
        case malformedURL
    }

    /// Отправляет тексты; возвращает индексы тех, что не удалось отправить.
    static func send(texts: [String]) async -> [Int] {
        guard !texts.isEmpty else { return [] }
        guard let environment = loadEnvironment() else { return Array(texts.indices) }
        do {
            try await sendAll(texts, environment)
            return []
        } catch {
            return Array(texts.indices)
        }
    }

    /// `nil` — приложение ещё не входило (или ключ недоступен расширению).
    static func loadEnvironment() -> Environment? {
        guard let defaults = UserDefaults(suiteName: appGroup) else { return nil }
        guard
            let accessToken = defaults.string(forKey: keyAccessToken), !accessToken.isEmpty,
            let deviceId = defaults.string(forKey: keyDeviceId), !deviceId.isEmpty,
            let baseURL = defaults.string(forKey: keyApiBaseURL), !baseURL.isEmpty,
            let key = loadKey()
        else {
            return nil
        }
        return Environment(
            baseURL: baseURL,
            accessToken: accessToken,
            deviceId: deviceId,
            key: key
        )
    }

    private static func loadKey() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data, data.count == SharePayload.keyLength else {
            return nil
        }
        return data
    }

    static func sendAll(_ texts: [String], _ environment: Environment) async throws {
        guard let url = webSocketURL(environment) else { throw SendError.malformedURL }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(environment.accessToken)", forHTTPHeaderField: "Authorization")

        let task = URLSession.shared.webSocketTask(with: request)
        task.resume()
        defer { task.cancel(with: .normalClosure, reason: nil) }

        for text in texts {
            let frame = try SharePayload.frame(text: text, key: environment.key, deviceId: environment.deviceId)
            try await task.send(.string(try decode(frame)))
        }
        try await task.send(.string(try decode(SharePayload.ping())))
        try await awaitServerConfirmation(task)
    }

    private static func decode(_ data: Data) throws -> String {
        guard let text = String(data: data, encoding: .utf8) else { throw SendError.disconnected }
        return text
    }

    /// Сервер отвечает `PONG` после `PING`; любой `ERROR` означает отказ.
    private static func awaitServerConfirmation(_ task: URLSessionWebSocketTask) async throws {
        try await withTimeout {
            while true {
                let message = try await task.receive()
                let text: String?
                switch message {
                case .string(let value):
                    text = value
                case .data(let value):
                    text = String(data: value, encoding: .utf8)
                @unknown default:
                    text = nil
                }
                guard let text, let type = SharePayload.messageType(text) else { continue }
                if type == "PONG" {
                    return
                }
                if type == "ERROR" {
                    throw SendError.serverError
                }
            }
        }
    }

    private static func withTimeout<T>(_ operation: @escaping () async throws -> T) async throws -> T {
        try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask { try await operation() }
            group.addTask {
                try await Task.sleep(nanoseconds: timeoutSeconds * 1_000_000_000)
                throw SendError.timeout
            }
            guard let result = try await group.next() else { throw SendError.timeout }
            group.cancelAll()
            return result
        }
    }

    private static func webSocketURL(_ environment: Environment) -> URL? {
        guard var components = URLComponents(string: environment.baseURL) else { return nil }
        components.scheme = components.scheme == "https" ? "wss" : "ws"
        let prefix = components.path.hasSuffix("/") ? components.path : components.path + "/"
        components.path = prefix + "v1/sync"
        components.queryItems = [URLQueryItem(name: "deviceId", value: environment.deviceId)]
        return components.url
    }
}
