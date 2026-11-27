import UIKit
import UniformTypeIdentifiers

/// Share Extension: принимает текст из любого приложения и сразу отправляет его
/// на сервер тем же E2EE-путём, что и основное приложение.
///
/// Если сессии/ключа/сети нет (приложение ещё не входило, access истёк), тексты
/// кладутся в App Group и уходят при следующем запуске основного приложения.
final class ShareViewController: UIViewController {

    private let statusLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        configureStatusLabel()
        collectText()
    }

    private func configureStatusLabel() {
        statusLabel.text = "Отправка в SyncClip…"
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 0
        statusLabel.textColor = .secondaryLabel
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(statusLabel)
        NSLayoutConstraint.activate([
            statusLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
        ])
    }

    private func collectText() {
        let items = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        let providers = items.flatMap { $0.attachments ?? [] }

        // Большинство приложений отдают один текст; последовательная загрузка
        // проще и не требует NSLock вокруг массива.
        Task { [weak self] in
            var texts: [String] = []
            for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                if let text = await Self.loadText(provider) {
                    texts.append(text)
                }
            }
            await self?.finish(texts: texts)
        }
    }

    private static func loadText(_ provider: NSItemProvider) async -> String? {
        await withCheckedContinuation { continuation in
            provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { item, _ in
                let text: String?
                switch item {
                case let string as String:
                    text = string
                case let data as Data:
                    text = String(data: data, encoding: .utf8)
                case let url as URL:
                    text = try? String(contentsOf: url, encoding: .utf8)
                default:
                    text = nil
                }
                let trimmed = text?.trimmingCharacters(in: .whitespacesAndNewlines)
                continuation.resume(returning: (trimmed?.isEmpty == false) ? text : nil)
            }
        }
    }

    @MainActor
    private func finish(texts: [String]) async {
        guard !texts.isEmpty else {
            complete()
            return
        }

        let failed = await ShareSender.send(texts: texts)
        let failedIndices = Set(failed)
        let sent = texts.enumerated().filter { !failedIndices.contains($0.offset) }.map(\.element)
        let failedTexts = failedIndices.sorted().map { texts[$0] }

        if !sent.isEmpty {
            store(sent, key: ShareSender.sentSharesKey)
        }
        if !failedTexts.isEmpty {
            statusLabel.text = "Не удалось отправить. Уйдёт при следующем запуске SyncClip."
            store(failedTexts, key: ShareSender.pendingSharesKey)
            // Даём прочитать причину, но не держим пользователя в шите.
            try? await Task.sleep(nanoseconds: 900_000_000)
        }
        complete()
    }

    private func complete() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }

    /// `sentShares` — только для истории приложения, `pendingShares` — дослать.
    private func store(_ texts: [String], key: String) {
        guard let defaults = UserDefaults(suiteName: ShareSender.appGroup) else { return }
        var queue = defaults.stringArray(forKey: key) ?? []
        queue.append(contentsOf: texts)
        // Ограничиваем общий контейнер: расширение не должно расти бесконечно.
        if queue.count > 50 {
            queue = Array(queue.suffix(50))
        }
        defaults.set(queue, forKey: key)
    }
}
