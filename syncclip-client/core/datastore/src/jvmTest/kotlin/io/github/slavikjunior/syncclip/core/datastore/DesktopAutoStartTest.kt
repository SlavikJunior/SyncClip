package io.github.slavikjunior.syncclip.core.datastore

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Автозапуск Desktop: файлы LaunchAgents/XDG и флаг в DataStore.
 *
 * Тест не трогает реальную ОС — home и ОС подставляются, запись проверяется на
 * диске во временном каталоге.
 */
internal class DesktopAutoStartTest {

    private val home: Path = Files.createTempDirectory("syncclip-autostart")
    private val dataStore = InMemoryPreferencesDataStore()
    private val executable = "/Applications/SyncClip.app/Contents/MacOS/SyncClip"

    @Test
    fun `mac launches at login and disables`() = runTest {
        val autoStart = DesktopAutoStart(
            dataStore = dataStore,
            home = home,
            osName = "Mac OS X",
            executable = executable,
        )
        val agent = AutoStartFiles.macPath(home)

        assertTrue(autoStart.isSupported)
        assertFalse(autoStart.isEnabled())

        assertTrue(autoStart.setEnabled(true))
        assertTrue(Files.exists(agent))
        val plist = Files.readString(agent)
        assertTrue(plist.contains("RunAtLoad"))
        assertTrue(plist.contains(executable))
        assertTrue(autoStart.isEnabled())

        assertTrue(autoStart.setEnabled(false))
        assertFalse(Files.exists(agent))
        assertFalse(autoStart.isEnabled())
    }

    @Test
    fun `linux writes xdg autostart entry`() = runTest {
        val autoStart = DesktopAutoStart(
            dataStore = dataStore,
            home = home,
            osName = "Linux",
            executable = "/opt/syncclip/bin/SyncClip",
        )
        val entry = AutoStartFiles.linuxPath(home)

        assertTrue(autoStart.setEnabled(true))

        val desktop = Files.readString(entry)
        assertTrue(desktop.contains("Exec=\"/opt/syncclip/bin/SyncClip\""))
        assertTrue(autoStart.isEnabled())
    }

    @Test
    fun `gradle run is not supported`() = runTest {
        val autoStart = DesktopAutoStart(
            dataStore = dataStore,
            home = home,
            osName = "Mac OS X",
            executable = null,
        )

        assertFalse(autoStart.isSupported)
        assertFalse(autoStart.setEnabled(true))
        assertFalse(autoStart.isEnabled())
    }
}
