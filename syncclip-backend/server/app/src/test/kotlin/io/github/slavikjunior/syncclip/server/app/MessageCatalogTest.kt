package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.app.i18n.MessageCatalog
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageCatalogTest {

    private val catalog = MessageCatalog()

    @Test
    fun `catalogues have identical key sets`() {
        val en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH).keySet()
        val ru = ResourceBundle.getBundle("i18n.messages", Locale("ru")).keySet()
        assertEquals(en, ru, "ru/en каталоги должны иметь одинаковый набор ключей")
    }

    @Test
    fun `every error code has a message in both locales`() {
        val en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH)
        val ru = ResourceBundle.getBundle("i18n.messages", Locale("ru"))
        ErrorCode.entries.forEach { code ->
            assertTrue(en.containsKey(code.restCode), "en: ${code.restCode}")
            assertTrue(ru.containsKey(code.restCode), "ru: ${code.restCode}")
        }
    }

    @Test
    fun `accept language resolves ru family and defaults to en`() {
        assertEquals("ru", catalog.locale("ru-RU,ru;q=0.9,en;q=0.8"))
        assertEquals("ru", catalog.locale("uk"))
        assertEquals("en", catalog.locale("de-DE"))
        assertEquals("en", catalog.locale(null))
    }

    @Test
    fun `messages are localized`() {
        val ru = catalog.message(ErrorCode.INVALID_CREDENTIALS.restCode, "ru")
        val en = catalog.message(ErrorCode.INVALID_CREDENTIALS.restCode, "en")
        assertTrue(ru.contains("Неверный"), ru)
        assertTrue(en.contains("Invalid"), en)
    }

    @Test
    fun `missing key falls back instead of throwing`() {
        assertEquals("Request failed", catalog.message("no_such_error_code", "ru"))
        assertEquals("Request failed", catalog.message("no_such_error_code", "en"))
    }
}
