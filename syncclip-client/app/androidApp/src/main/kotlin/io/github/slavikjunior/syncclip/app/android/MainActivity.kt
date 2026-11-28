package io.github.slavikjunior.syncclip.app.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.slavikjunior.syncclip.app.android.documents.AndroidDocumentGateway
import io.github.slavikjunior.syncclip.app.android.security.AndroidBiometricAuthenticator
import io.github.slavikjunior.syncclip.app.common.ui.SyncClipApp
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* состояние не блокирует вход */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Платформенные реализации, которым нужна Activity: диалог файлов
        // (SAF) и подтверждение личности (Keyguard). Регистрируются до
        // STARTED — этого требуют launcher'ы Activity Result API.
        val documents = AndroidDocumentGateway(this)
        val biometrics = AndroidBiometricAuthenticator(this)
        loadKoinModules(
            module {
                single<DocumentGateway> { documents }
                single<BiometricAuthenticator> { biometrics }
            },
        )

        requestNotificationPermission()

        setContent {
            SyncClipApp()
        }
    }

    /** Android 13+: без разрешения FCM-уведомления не показываются (FR-1.5). */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
