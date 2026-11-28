package io.github.slavikjunior.syncclip.app.ios.documents

import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import io.github.slavikjunior.syncclip.core.platform.FileOpenResult
import io.github.slavikjunior.syncclip.core.platform.FileSaveResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Системный диалог файлов iOS: `UIDocumentPickerViewController` (US-018, US-024).
 *
 * Экспорт пишет временный файл и предлагает сохранить его через picker;
 * импорт открывает picker для JSON/CSV и читает выбранный файл с лимитом.
 * Делегаты хранятся в свойствах: UIKit не удерживает их сам.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosDocumentGateway : DocumentGateway {

    override val isSupported: Boolean = true

    private var saveContinuation: CancellableContinuation<NSURL?>? = null
    private var openContinuation: CancellableContinuation<NSURL?>? = null

    private val saveDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            saveContinuation?.let { continuation ->
                saveContinuation = null
                continuation.resume(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            saveContinuation?.let { continuation ->
                saveContinuation = null
                continuation.resume(null)
            }
        }
    }

    private val openDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            openContinuation?.let { continuation ->
                openContinuation = null
                continuation.resume(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            openContinuation?.let { continuation ->
                openContinuation = null
                continuation.resume(null)
            }
        }
    }

    override suspend fun save(
        fileName: String,
        content: String,
        mimeType: String,
    ): FileSaveResult {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        val written = NSString.create(string = content).writeToURL(
            url = url,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (!written) return FileSaveResult.Failed

        val picker = UIDocumentPickerViewController(
            forExportingURLs = listOf(url),
            asCopy = true,
        )
        picker.delegate = saveDelegate
        val picked = awaitPick(picker, isSave = true)
        return if (picked == null) FileSaveResult.Cancelled else FileSaveResult.Saved(fileName)
    }

    override suspend fun open(mimeTypes: List<String>): FileOpenResult {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeJSON, UTTypeCommaSeparatedText),
            asCopy = true,
        )
        picker.delegate = openDelegate
        val url = awaitPick(picker, isSave = false) ?: return FileOpenResult.Cancelled
        val accessed = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: return FileOpenResult.Failed
            if (data.length.toLong() > MAX_IMPORT_BYTES) return FileOpenResult.Failed
            val content = NSString.create(data = data, encoding = NSUTF8StringEncoding)
                ?.toString()
                ?: return FileOpenResult.Failed
            return FileOpenResult.Opened(
                name = url.lastPathComponent ?: "archive",
                content = content,
            )
        } finally {
            if (accessed) url.stopAccessingSecurityScopedResource()
        }
    }

    private suspend fun awaitPick(
        picker: UIDocumentPickerViewController,
        isSave: Boolean,
    ): NSURL? = suspendCancellableCoroutine { continuation ->
        if (isSave) saveContinuation = continuation else openContinuation = continuation
        continuation.invokeOnCancellation {
            if (isSave) saveContinuation = null else openContinuation = null
        }
        val presenter = rootViewController()
        if (presenter == null) {
            if (isSave) saveContinuation = null else openContinuation = null
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private fun rootViewController(): UIViewController? {
        val window = UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
        return window?.rootViewController
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 8L * 1024 * 1024
    }
}
