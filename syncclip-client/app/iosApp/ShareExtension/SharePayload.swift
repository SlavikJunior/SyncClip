import Foundation
import CryptoKit

/// Формирование кадра `SYNC_EVENT` в том же формате, что `:shared:crypto` и
/// `:feature:sync:impl` (см. WEBSOCKET_PROTOCOL §3.1).
///
/// Share Extension не линкует Kotlin-фреймворк: расширению нужен только
/// AES-256-GCM и SHA-256, которые даёт CryptoKit. Совпадение с Kotlin
/// зафиксировано вектором `AesGcmShareVectorTest` (Kotlin) → `SharePayloadTests`.
enum SharePayload {

    static let ivLength = 12
    static let keyLength = 32

    /// `base64(IV (12) || ciphertext || tag (16))` — ровно `encryptedPayload`.
    static func encryptedPayload(
        text: String,
        key: Data,
        iv: Data = randomIV()
    ) throws -> String {
        precondition(key.count == keyLength, "E2EE key must be 32 bytes")
        precondition(iv.count == ivLength, "GCM requires a 12-byte IV")
        let plaintext = Data(text.utf8)
        let sealed = try AES.GCM.seal(
            plaintext,
            using: SymmetricKey(data: key),
            nonce: try AES.GCM.Nonce(data: iv)
        )
        return (iv + sealed.ciphertext + sealed.tag).base64EncodedString()
    }

    /// SHA-256 открытого текста в нижнем hex — поле `payloadHash`.
    static func payloadHash(text: String) -> String {
        SHA256.hash(data: Data(text.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }

    /// JSON-кадр для сервера; порядок ключей фиксирован `.sortedKeys`.
    static func frame(
        text: String,
        key: Data,
        deviceId: String,
        eventId: String = UUID().uuidString,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) throws -> Data {
        let body: [String: Any] = [
            "type": "SYNC_EVENT",
            "eventId": eventId,
            "originDeviceId": deviceId,
            "targetDeviceIds": [],
            "encryptedPayload": try encryptedPayload(text: text, key: key),
            "payloadHash": payloadHash(text: text),
            "timestamp": timestamp,
        ]
        return try JSONSerialization.data(withJSONObject: body, options: [.sortedKeys])
    }

    static func ping(timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) throws -> Data {
        try JSONSerialization.data(
            withJSONObject: ["type": "PING", "timestamp": timestamp],
            options: [.sortedKeys]
        )
    }

    /// Тип серверного кадра (`PONG`, `ERROR`, …) без полной модели сообщений.
    static func messageType(_ text: String) -> String? {
        guard
            let data = text.data(using: .utf8),
            let body = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return nil
        }
        return body["type"] as? String
    }

    static func randomIV() -> Data {
        Data((0..<ivLength).map { _ in UInt8.random(in: UInt8.min...UInt8.max) })
    }
}
