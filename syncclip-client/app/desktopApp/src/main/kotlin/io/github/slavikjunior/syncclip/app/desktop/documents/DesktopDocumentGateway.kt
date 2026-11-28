package io.github.slavikjunior.syncclip.app.desktop.documents

import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import io.github.slavikjunior.syncclip.core.platform.FileOpenResult
import io.github.slavikjunior.syncclip.core.platform.FileSaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Системный диалог файлов Desktop (US-018, US-024).
 *
 * Swing-диалог модальный и обязан открываться на EDT — `Dispatchers.Swing`
 * гарантирует это, поэтому вызов безопасен и из `viewModelScope`.
 */
internal class DesktopDocumentGateway : DocumentGateway {

    override val isSupported: Boolean = true

    override suspend fun save(
        fileName: String,
        content: String,
        mimeType: String,
    ): FileSaveResult = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser().apply {
            dialogTitle = "SyncClip"
            selectedFile = File(fileName)
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return@withContext FileSaveResult.Cancelled
        }
        val file = chooser.selectedFile ?: return@withContext FileSaveResult.Failed
        runCatching { file.writeText(content) }
            .fold(
                onSuccess = { FileSaveResult.Saved(file.absolutePath) },
                onFailure = { FileSaveResult.Failed },
            )
    }

    override suspend fun open(mimeTypes: List<String>): FileOpenResult =
        withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                dialogTitle = "SyncClip"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("SyncClip archive", "json", "csv")
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                return@withContext FileOpenResult.Cancelled
            }
            val file = chooser.selectedFile ?: return@withContext FileOpenResult.Failed
            // Лимит защищает от чтения гигабайтного файла в память.
            if (!file.isFile || file.length() > MAX_IMPORT_BYTES) {
                return@withContext FileOpenResult.Failed
            }
            runCatching { FileOpenResult.Opened(file.name, file.readText()) }
                .getOrDefault(FileOpenResult.Failed)
        }

    private companion object {
        const val MAX_IMPORT_BYTES = 8L * 1024 * 1024
    }
}
