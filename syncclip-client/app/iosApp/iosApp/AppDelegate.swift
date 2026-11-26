import UIKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications
import ComposeApp

/// Точка входа нативных сервисов: Firebase и APNs.
///
/// `FirebaseApp.configure()` обязан выполниться до первого обращения к
/// FirebaseAuth из Kotlin, поэтому мост регистрируется здесь, а не в SwiftUI.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()

        let bridge = FirebaseBridge()
        FirebaseBridge.shared = bridge
        IosFirebaseBridge.shared.install(delegate: bridge)

        Messaging.messaging().delegate = bridge
        UNUserNotificationCenter.current().delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    /// APNs-токен конвертируется в FCM автоматически после установки `apnsToken`.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("SyncClip: APNs registration failed: \(error.localizedDescription)")
    }

    /// Push в foreground показываем баннером: FCM не делает этого сам.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }
}
