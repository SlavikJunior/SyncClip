package io.github.slavikjunior.syncclip.core.firebase

/**
 * Мост iOS: Firebase iOS SDK живёт в Swift, Kotlin получает только контракт.
 *
 * Причина: Firebase iOS SDK подключается через SPM в Xcode-проекте `iosApp`,
 * а Kotlin/Native не должен тянуть cinterop на десятки заголовков ради двух
 * вызовов. Swift-класс реализует [IosFirebaseBridgeDelegate] и регистрируется
 * при старте приложения:
 *
 * ```swift
 * IosFirebaseBridge.shared.install(delegate: FirebaseBridge())
 * ```
 */
public interface IosFirebaseBridgeDelegate {

    fun observeAuth(onChange: (FirebaseUser?) -> Unit): IosFirebaseSubscription

    fun currentUser(): FirebaseUser?

    fun signUpWithEmail(
        email: String,
        password: String,
        completion: (FirebaseAuthResult) -> Unit,
    )

    fun signInWithEmail(
        email: String,
        password: String,
        completion: (FirebaseAuthResult) -> Unit,
    )

    fun signInWithGoogle(
        idToken: String,
        accessToken: String?,
        completion: (FirebaseAuthResult) -> Unit,
    )

    fun sendPasswordReset(
        email: String,
        completion: (FirebaseOperationResult) -> Unit,
    )

    fun idToken(forceRefresh: Boolean, completion: (String?) -> Unit)

    fun signOut()

    fun requestNotificationPermission(completion: (Boolean) -> Unit)

    fun messagingToken(completion: (String?) -> Unit)

    fun observeMessagingToken(onChange: (String) -> Unit): IosFirebaseSubscription
}

/** Отписка от нативного слушателя Firebase (auth state, APNs-токен). */
public interface IosFirebaseSubscription {
    fun dispose()
}

/** Реестр Swift-реализации; внедряется один раз при старте приложения. */
public object IosFirebaseBridge {

    private var installed: IosFirebaseBridgeDelegate? = null

    public fun install(delegate: IosFirebaseBridgeDelegate) {
        installed = delegate
    }

    internal fun delegateOrNull(): IosFirebaseBridgeDelegate? = installed
}
