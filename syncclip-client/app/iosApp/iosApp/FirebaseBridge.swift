import Foundation
import UIKit
import FirebaseAuth
import FirebaseMessaging
import UserNotifications
import ComposeApp

/// Реализация Kotlin-контракта `IosFirebaseBridgeDelegate` поверх Firebase iOS SDK.
///
/// Kotlin не тянет cinterop Firebase: весь нативный код живёт здесь, а общий
/// слой получает результат в терминах `FirebaseAuthResult`.
final class FirebaseBridge: NSObject, IosFirebaseBridgeDelegate, MessagingDelegate {

    static var shared: FirebaseBridge?

    private var tokenObservers: [UUID: (String) -> Void] = [:]

    // MARK: - Auth

    func observeAuth(onChange: @escaping (FirebaseUser?) -> Void) -> IosFirebaseSubscription {
        let handle = Auth.auth().addStateDidChangeListener { _, user in
            onChange(user.map(Self.toAppUser))
        }
        if let user = Auth.auth().currentUser {
            onChange(Self.toAppUser(user))
        }
        return Subscription { Auth.auth().removeStateDidChangeListener(handle) }
    }

    func currentUser() -> FirebaseUser? {
        Auth.auth().currentUser.map(Self.toAppUser)
    }

    func signUpWithEmail(
        email: String,
        password: String,
        completion: @escaping (FirebaseAuthResult) -> Void
    ) {
        Task { @MainActor in
            do {
                let result = try await Auth.auth().createUser(withEmail: email, password: password)
                completion(FirebaseAuthResultSuccess(user: Self.toAppUser(result.user)))
            } catch {
                completion(FirebaseAuthResultFailure(reason: Self.map(error)))
            }
        }
    }

    func signInWithEmail(
        email: String,
        password: String,
        completion: @escaping (FirebaseAuthResult) -> Void
    ) {
        Task { @MainActor in
            do {
                let result = try await Auth.auth().signIn(withEmail: email, password: password)
                completion(FirebaseAuthResultSuccess(user: Self.toAppUser(result.user)))
            } catch {
                completion(FirebaseAuthResultFailure(reason: Self.map(error)))
            }
        }
    }

    func signInWithGoogle(
        idToken: String,
        accessToken: String?,
        completion: @escaping (FirebaseAuthResult) -> Void
    ) {
        let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken ?? "")
        Task { @MainActor in
            do {
                let result = try await Auth.auth().signIn(with: credential)
                completion(FirebaseAuthResultSuccess(user: Self.toAppUser(result.user)))
            } catch {
                completion(FirebaseAuthResultFailure(reason: Self.map(error)))
            }
        }
    }

    func sendPasswordReset(
        email: String,
        completion: @escaping (FirebaseOperationResult) -> Void
    ) {
        Task { @MainActor in
            do {
                try await Auth.auth().sendPasswordReset(withEmail: email)
                completion(FirebaseOperationResultSuccess.shared)
            } catch {
                completion(FirebaseOperationResultFailure(reason: Self.map(error)))
            }
        }
    }

    func idToken(forceRefresh: Bool, completion: @escaping (String?) -> Void) {
        guard let user = Auth.auth().currentUser else {
            completion(nil)
            return
        }
        Task { @MainActor in
            completion(try? await user.getIDToken(forcingRefresh: forceRefresh))
        }
    }

    func signOut() {
        try? Auth.auth().signOut()
    }

    // MARK: - Messaging

    func requestNotificationPermission(completion: @escaping (KotlinBoolean) -> Void) {
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                DispatchQueue.main.async {
                    if granted {
                        UIApplication.shared.registerForRemoteNotifications()
                    }
                    completion(KotlinBoolean(bool: granted))
                }
            }
    }

    func messagingToken(completion: @escaping (String?) -> Void) {
        Messaging.messaging().token { token, _ in
            completion(token)
        }
    }

    func observeMessagingToken(onChange: @escaping (String) -> Void) -> IosFirebaseSubscription {
        let id = UUID()
        tokenObservers[id] = onChange
        return Subscription { [weak self] in
            self?.tokenObservers[id] = nil
        }
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        tokenObservers.values.forEach { observer in observer(fcmToken) }
    }

    // MARK: - Mapping

    private static func toAppUser(_ user: User) -> FirebaseUser {
        FirebaseUser(
            uid: user.uid,
            email: user.email ?? "",
            displayName: user.displayName,
            isEmailVerified: user.isEmailVerified
        )
    }

    private static func map(_ error: Error) -> FirebaseAuthFailure {
        switch AuthErrorCode(rawValue: (error as NSError).code) {
        case .invalidCredential, .wrongPassword, .userNotFound:
            return .invalidCredentials
        case .emailAlreadyInUse:
            return .emailAlreadyInUse
        case .weakPassword:
            return .weakPassword
        case .invalidEmail:
            return .invalidEmail
        case .networkError:
            return .network
        case .operationNotAllowed:
            return .notSupported
        default:
            return .unknown
        }
    }
}

/// Отписка от нативного слушателя.
final class Subscription: NSObject, IosFirebaseSubscription {

    private let disposeBlock: () -> Void

    init(_ dispose: @escaping () -> Void) {
        self.disposeBlock = dispose
    }

    func dispose() {
        disposeBlock()
    }
}
