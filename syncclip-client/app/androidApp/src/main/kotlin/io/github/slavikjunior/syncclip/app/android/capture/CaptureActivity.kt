package io.github.slavikjunior.syncclip.app.android.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.slavikjunior.syncclip.app.android.R
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Прозрачная точка входа захвата вне основного UI (FR-3.2).
 *
 * - `ACTION_SEND` + `text/plain` — Share Sheet: текст передаётся явно, буфер не
 *   трогаем;
 * - [ACTION_CAPTURE_CLIPBOARD] — Quick Settings Tile: Activity получает фокус,
 *   поэтому системный буфер доступен (Android 10+ отдаёт его только приложению
 *   с фокусом), и читает его через `captureCurrent()`.
 *
 * Activity ничего не рисует и сразу закрывается: пользователь видит только
 * Toast о результате.
 */
class CaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val starter = GlobalContext.get().get<ClipboardCaptureStarter>()
        val sharedText = sharedText(intent)
        val isClipboardCapture = intent?.action == ACTION_CAPTURE_CLIPBOARD
        if (sharedText == null && !isClipboardCapture) {
            finish()
            return
        }

        lifecycleScope.launch {
            val accepted = if (sharedText != null) {
                starter.captureText(sharedText)
            } else {
                starter.captureCurrent()
            }
            val message = if (accepted) R.string.capture_done else R.string.capture_empty
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val ACTION_CAPTURE_CLIPBOARD =
            "io.github.slavikjunior.syncclip.action.CAPTURE_CLIPBOARD"
    }
}
