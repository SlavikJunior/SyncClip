package io.github.slavikjunior.syncclip.core.datastore

import platform.Foundation.NSUserDefaults

/**
 * Зеркало сессии в App Group для Share Extension.
 *
 * Расширение читает те же ключи через `UserDefaults(suiteName:)` в Swift.
 * `accessToken` обновляется при каждой ротации пары; refresh-токен не
 * публикуется — ротацией владеет приложение.
 */
internal class IosShareBridge : ShareBridge {

    override fun session(userId: String?, accessToken: String?) {
        val defaults = NSUserDefaults(suiteName = APP_GROUP) ?: return
        defaults.setObject(userId, forKey = KEY_USER_ID)
        defaults.setObject(accessToken, forKey = KEY_ACCESS_TOKEN)
    }

    override fun device(deviceId: String) {
        NSUserDefaults(suiteName = APP_GROUP)?.setObject(deviceId, forKey = KEY_DEVICE_ID)
    }

    override fun endpoint(baseUrl: String) {
        NSUserDefaults(suiteName = APP_GROUP)?.setObject(baseUrl, forKey = KEY_API_BASE_URL)
    }

    private companion object {
        const val APP_GROUP = "group.io.github.slavikjunior.syncclip"
        const val KEY_USER_ID = "share.user_id"
        const val KEY_ACCESS_TOKEN = "share.access_token"
        const val KEY_DEVICE_ID = "share.device_id"
        const val KEY_API_BASE_URL = "share.api_base_url"
    }
}

internal actual fun shareBridge(): ShareBridge = IosShareBridge()
