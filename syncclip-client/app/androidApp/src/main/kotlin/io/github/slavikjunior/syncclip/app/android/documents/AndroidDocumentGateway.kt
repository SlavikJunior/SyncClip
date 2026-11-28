package io.github.slavikjunior.syncclip.app.android.documents

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import io.github.slavikjunior.syncclip.core.platform.FileOpenResult
import io.github.slavikjunior.syncclip.core.platform.FileSaveResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Системный диалог файлов через Storage Access Framework (US-018, US-024).
 *
 * Разрешения на хранилище не нужны: пользователь сам выбирает файл, а доступ
 * выдаётся на один URI. Launcher'ы регистрируются в `onCreate` (до STARTED) —
 * это требование `registerForActivityResult`.
 */
internal class AndroidDocumentGateway(
    private val activity: ComponentActivity,
) : DocumentGateway {

    override val isSupported: Boolean = true

    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    private val saveJson = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME),
    ) { uri -> pendingSave?.complete(uri) }

    private val saveCsv = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME),
    ) { uri -> pendingSave?.complete(uri) }

    private val openDocument = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingOpen?.complete(uri) }

    override suspend fun save(
        fileName: String,
        content: String,
        mimeType: String,
    ): FileSaveResult {
        val launcher = if (mimeType == CSV_MIME) saveCsv else saveJson
        val deferred = CompletableDeferred<Uri?>()
        pendingSave = deferred
        launcher.launch(fileName)
        val uri = deferred.await() ?: return FileSaveResult.Cancelled
        // Запись — в IO: файл может быть на сетевом хранилище, а кадр UI
        // не должен ждать.
        val written = withContext(Dispatchers.IO) {
            runCatching {
                activity.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray())
                } ?: error("stream is unavailable")
            }.isSuccess
        }
        return if (written) {
            FileSaveResult.Saved(location = uri.lastPathSegment ?: fileName)
        } else {
            FileSaveResult.Failed
        }
    }

    override suspend fun open(mimeTypes: List<String>): FileOpenResult {
        val deferred = CompletableDeferred<Uri?>()
        pendingOpen = deferred
        openDocument.launch(mimeTypes.toTypedArray())
        val uri = deferred.await() ?: return FileOpenResult.Cancelled
        val content = withContext(Dispatchers.IO) {
            runCatching {
                activity.contentResolver.openInputStream(uri)?.use { stream ->
                    // Читаем с запасом один байт: так видно, что файл больше лимита.
                    // `readNBytes` требует API 33, поэтому цикл ручной.
                    val buffer = ByteArray(MAX_IMPORT_BYTES + 1)
                    var read = 0
                    while (read < buffer.size) {
                        val count = stream.read(buffer, read, buffer.size - read)
                        if (count < 0) break
                        read += count
                    }
                    if (read > MAX_IMPORT_BYTES) null else buffer.decodeToString(0, read)
                }
            }.getOrNull()
        } ?: return FileOpenResult.Failed
        return FileOpenResult.Opened(
            name = uri.lastPathSegment ?: "archive",
            content = content,
        )
    }

    private companion object {
        const val JSON_MIME = "application/json"
        const val CSV_MIME = "text/csv"

        /** 8 МиБ: история в 1000 записей укладывается с большим запасом. */
        const val MAX_IMPORT_BYTES = 8 * 1024 * 1024
    }
}
