package io.github.slavikjunior.syncclip.core.clipboard

import java.awt.GraphicsEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Проверка Desktop-реализации на настоящем системном буфере.
 *
 * В headless-окружении (CI без графической сессии) тест ничего не делает:
 * AWT-буфер там недоступен, и падение не означало бы ошибку кода.
 */
internal class AwtSystemClipboardTest {

    @Test
    fun `written text is read back`() = runBlocking {
        if (GraphicsEnvironment.isHeadless()) return@runBlocking
        val clipboard = createSystemClipboard()
        val original = clipboard.read()
        val probe = "syncclip-probe-${kotlin.uuid.Uuid.random()}"
        try {
            clipboard.write(probe)
            assertEquals(probe, clipboard.read())
        } finally {
            if (original != null) clipboard.write(original)
        }
    }

    @Test
    fun `external change is observed`() = runBlocking {
        if (GraphicsEnvironment.isHeadless()) return@runBlocking
        val clipboard = createSystemClipboard()
        val original = clipboard.read()
        val observed = CompletableDeferred<String>()
        val collector = launch(Dispatchers.Default) {
            clipboard.changes().collect { change ->
                if (!observed.isCompleted) observed.complete(change.text)
            }
        }
        val probe = "syncclip-change-${kotlin.uuid.Uuid.random()}"
        try {
            // Даём наблюдателю прочитать исходное содержимое и встать на опрос.
            delay(700)
            clipboard.write(probe)

            assertEquals(probe, withTimeoutOrNull(5_000) { observed.await() })
        } finally {
            collector.cancelAndJoin()
            if (original != null) clipboard.write(original)
        }
    }
}
