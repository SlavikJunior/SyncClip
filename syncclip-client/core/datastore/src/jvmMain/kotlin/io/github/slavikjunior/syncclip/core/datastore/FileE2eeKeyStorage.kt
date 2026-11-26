package io.github.slavikjunior.syncclip.core.datastore

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ключ E2EE в файле с правами владельца.
 *
 * OS credential manager на Desktop недоступен без нативной библиотеки, поэтому
 * ключ лежит в `~/.syncclip/e2ee.key` с правами `rw-------`. Это слабее
 * Keychain/Keystore (защита — правами ФС и правами пользователя), ограничение
 * зафиксировано в `PROFILE.md`.
 */
internal class FileE2eeKeyStorage(
    private val file: Path = defaultKeyFile(),
) : E2eeKeyStorage {

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        if (!Files.exists(file)) return@withContext null
        Files.readAllBytes(file).takeIf { it.size == KEY_BYTES }
    }

    override suspend fun save(key: ByteArray) {
        withContext(Dispatchers.IO) {
            val directory = file.parent ?: return@withContext
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, "e2ee", ".tmp")
            try {
                Files.write(temporary, key, StandardOpenOption.TRUNCATE_EXISTING)
                restrictToOwner(temporary)
                // Атомарная замена: оборванная запись не оставит полуфайла.
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                Files.deleteIfExists(temporary)
            }
            restrictToOwner(file)
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { Files.deleteIfExists(file) }
    }

    private fun restrictToOwner(path: Path) {
        // На Windows POSIX-прав нет: файл защищён правами профиля пользователя.
        runCatching {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        }
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}

/** `~/.syncclip/e2ee.key`: рядом с БД и настройками Desktop-клиента. */
private fun defaultKeyFile(): Path = Path.of(
    System.getProperty("user.home"),
    ".syncclip",
    "e2ee.key",
)

internal actual fun createE2eeKeyStorage(platformContext: Any?): E2eeKeyStorage =
    FileE2eeKeyStorage()
