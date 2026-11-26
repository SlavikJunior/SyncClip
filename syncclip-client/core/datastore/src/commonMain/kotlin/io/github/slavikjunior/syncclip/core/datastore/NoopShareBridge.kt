package io.github.slavikjunior.syncclip.core.datastore

/** Заглушка для платформ без расширения (Android, Desktop). */
internal object NoopShareBridge : ShareBridge {

    override fun session(userId: String?, accessToken: String?) = Unit

    override fun device(deviceId: String) = Unit

    override fun endpoint(baseUrl: String) = Unit
}
