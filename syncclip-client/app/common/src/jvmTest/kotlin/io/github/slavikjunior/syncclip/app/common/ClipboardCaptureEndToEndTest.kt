package io.github.slavikjunior.syncclip.app.common

import io.github.slavikjunior.syncclip.app.common.di.appModules
import io.github.slavikjunior.syncclip.core.clipboard.SystemClipboard
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Сквозная проверка захвата на Desktop: настоящий системный буфер → Koin-граф →
 * Room.
 *
 * Проверяет именно wiring, которое не видно компилятору: gateway подключён к
 * конвейеру, конвейер пишет в настоящую БД, composition root запускает starter.
 * В headless-окружении AWT-буфера нет — тест пропускается, а не падает.
 */
internal class ClipboardCaptureEndToEndTest {

    @Test
    fun `external copy reaches local history`() = runBlocking {
        if (GraphicsEnvironment.isHeadless()) return@runBlocking

        val home = Files.createTempDirectory("syncclip-e2e").toFile()
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", home.absolutePath)
        val koin = koinApplication { modules(appModules()) }.koin

        val clipboard: SystemClipboard = koin.get()
        val history: ClipboardEventDao = koin.get()
        val starter: ClipboardCaptureStarter = koin.get()
        val original = clipboard.read()
        val probe = "syncclip-e2e-${Uuid.random()}"
        val pipeline = launch(Dispatchers.Default) { starter.run() }

        try {
            // Даём опросу буфера прочитать исходное содержимое и встать на цикл.
            delay(700)
            clipboard.write(probe)

            val found = withTimeout(OR_TIMEOUT_MILLIS) {
                history.observePage(limit = 10, offset = 0)
                    .first { page -> page.any { it.text == probe } }
            }
            assertTrue(found.any { it.text == probe })
            assertTrue(found.first { it.text == probe }.sourceDeviceId == null)
        } catch (error: IllegalStateException) {
            fail("Копирование не дошло до истории за $OR_TIMEOUT_MILLIS мс: ${error.message}")
        } finally {
            pipeline.cancelAndJoin()
            if (original != null) clipboard.write(original)
            System.setProperty("user.home", previousHome)
            koin.close()
            home.deleteRecursively()
        }
    }

    private companion object {
        const val OR_TIMEOUT_MILLIS = 10_000L
    }
}
