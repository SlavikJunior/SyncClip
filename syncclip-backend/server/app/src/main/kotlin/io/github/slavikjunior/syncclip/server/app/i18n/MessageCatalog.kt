package io.github.slavikjunior.syncclip.server.app.i18n

import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

/**
 * Каталог сообщений ошибок (FR-8.3): ключ = `ErrorCode.restCode`.
 * `Accept-Language` → ru (ru/by/uk) или en; отсутствующий ключ → en.
 */
class MessageCatalog {

    fun locale(acceptLanguage: String?): String =
        if (acceptLanguage.isNullOrBlank()) {
            "en"
        } else {
            val primary = acceptLanguage.substringBefore(',').substringBefore(';').trim().lowercase()
            if (primary.startsWith("ru") || primary.startsWith("be") || primary.startsWith("uk")) "ru" else "en"
        }

    fun message(code: String, locale: String): String {
        val localized = runCatching { bundle(locale).getString(code) }.getOrNull()
        if (!localized.isNullOrBlank()) return localized
        // Новый ErrorCode без .properties не должен ломать обработчик ошибок (LOW-07).
        return runCatching { bundle("en").getString(code) }.getOrDefault(FALLBACK_MESSAGE)
    }

    private fun bundle(locale: String): ResourceBundle = try {
        ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(locale))
    } catch (e: MissingResourceException) {
        ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH)
    }

    private companion object {
        const val FALLBACK_MESSAGE = "Request failed"
    }
}
