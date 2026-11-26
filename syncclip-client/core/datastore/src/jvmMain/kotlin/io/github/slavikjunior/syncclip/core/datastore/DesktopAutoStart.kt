package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.first

/**
 * Автозапуск Desktop через штатные механизмы ОС.
 *
 * Приложение должно быть упаковано (`jpackage`): при запуске из Gradle
 * исполняемый файл — это `java`, и автозапуск такого процесса бессмыслен.
 */
internal class DesktopAutoStart(
    private val dataStore: DataStore<Preferences>,
    private val home: Path = Path.of(System.getProperty("user.home")),
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val executable: String? = resolveExecutable(),
) : AutoStart {

    private val entry: AutoStartEntry? = executable?.let { path ->
        when {
            osName.startsWith("Mac", ignoreCase = true) -> MacLaunchAgent(home, path)
            osName.startsWith("Windows", ignoreCase = true) -> WindowsRunKey(path)
            else -> LinuxDesktopEntry(home, path)
        }
    }

    override val isSupported: Boolean
        get() = entry != null

    override suspend fun isEnabled(): Boolean {
        if (!isSupported) return false
        val stored = runCatching { dataStore.data.first()[AUTOSTART] }.getOrNull() ?: false
        if (!stored) return false
        return entry?.exists() ?: false
    }

    override suspend fun setEnabled(enabled: Boolean): Boolean {
        val target = entry ?: return false
        return runCatching {
            if (enabled) target.enable() else target.disable()
            dataStore.edit { preferences -> preferences[AUTOSTART] = enabled }
            true
        }.getOrDefault(false)
    }

    private companion object {
        val AUTOSTART = booleanPreferencesKey("desktop.autostart")
    }
}

/** Запись автозапуска конкретной ОС. */
internal sealed interface AutoStartEntry {

    fun enable()

    fun disable()

    fun exists(): Boolean
}

/** macOS: `~/Library/LaunchAgents/<label>.plist` с `RunAtLoad`. */
internal class MacLaunchAgent(
    private val home: Path,
    private val executable: String,
) : AutoStartEntry {

    private val file: Path = AutoStartFiles.macPath(home)

    override fun enable() {
        Files.createDirectories(file.parent)
        Files.writeString(file, AutoStartFiles.macPlist(executable))
    }

    override fun disable() {
        Files.deleteIfExists(file)
    }

    override fun exists(): Boolean = Files.exists(file)
}

/** Linux (XDG): `~/.config/autostart/syncclip.desktop`. */
internal class LinuxDesktopEntry(
    private val home: Path,
    private val executable: String,
) : AutoStartEntry {

    private val file: Path = AutoStartFiles.linuxPath(home)

    override fun enable() {
        Files.createDirectories(file.parent)
        Files.writeString(file, AutoStartFiles.linuxDesktopEntry(executable))
    }

    override fun disable() {
        Files.deleteIfExists(file)
    }

    override fun exists(): Boolean = Files.exists(file)
}

/** Windows: ключ `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`. */
internal class WindowsRunKey(
    private val executable: String,
) : AutoStartEntry {

    override fun enable() {
        runReg("add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", executable, "/f")
    }

    override fun disable() {
        runReg("delete", RUN_KEY, "/v", VALUE_NAME, "/f")
    }

    override fun exists(): Boolean = true

    private fun runReg(vararg args: String) {
        val process = ProcessBuilder("reg", *args)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
    }

    private companion object {
        const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE_NAME = "SyncClip"
    }
}

/** Содержимое и пути файлов автозапуска — отдельно, чтобы тестировать без ОС. */
internal object AutoStartFiles {

    fun macPath(home: Path): Path =
        home.resolve("Library/LaunchAgents/io.github.slavikjunior.syncclip.plist")

    fun linuxPath(home: Path): Path =
        home.resolve(".config/autostart/syncclip.desktop")

    fun macPlist(executable: String): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        |<plist version="1.0">
        |<dict>
        |    <key>Label</key>
        |    <string>io.github.slavikjunior.syncclip</string>
        |    <key>ProgramArguments</key>
        |    <array>
        |        <string>${escapeXml(executable)}</string>
        |    </array>
        |    <key>RunAtLoad</key>
        |    <true/>
        |</dict>
        |</plist>
        |
    """.trimMargin()

    fun linuxDesktopEntry(executable: String): String = """
        |[Desktop Entry]
        |Type=Application
        |Name=SyncClip
        |Comment=Clipboard sync
        |Exec="${executable.replace("\"", "\\\"")}"
        |X-GNOME-Autostart-enabled=true
        |
    """.trimMargin()

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

/**
 * @return путь к упакованному приложению или `null`, если запуск идёт из JVM
 *   (Gradle): автозапуск `java` без аргументов не сработает.
 */
private fun resolveExecutable(): String? {
    System.getProperty("jpackage.app-path")?.let { return it }
    val command = ProcessHandle.current().info().command().orElse(null) ?: return null
    val name = Path.of(command).fileName?.toString()?.lowercase() ?: return null
    return if (name.startsWith("java")) null else command
}

public actual fun createAutoStart(
    dataStore: DataStore<Preferences>,
    platformContext: Any?,
): AutoStart = DesktopAutoStart(dataStore)
