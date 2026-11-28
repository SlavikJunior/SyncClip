package io.github.slavikjunior.syncclip.core.di

import io.github.slavikjunior.syncclip.core.archive.HistoryArchiveStore
import io.github.slavikjunior.syncclip.core.archive.createHistoryArchiveStore
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import io.github.slavikjunior.syncclip.core.platform.IncomingClipNotifier
import io.github.slavikjunior.syncclip.core.platform.NoopIncomingClipNotifier
import io.github.slavikjunior.syncclip.core.platform.UnsupportedBiometricAuthenticator
import io.github.slavikjunior.syncclip.core.platform.UnsupportedDocumentGateway
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Платформенные возможности и архив истории.
 *
 * Заглушки регистрируются здесь, а точки входа приложений перекрывают их
 * `override = true`: экран настроек не должен проверять `null` и прятать
 * половину секций на платформе, где реализация есть.
 */
public fun platformServicesModule(): Module = module {
    single<DocumentGateway> { UnsupportedDocumentGateway }
    single<BiometricAuthenticator> { UnsupportedBiometricAuthenticator }
    single<IncomingClipNotifier> { NoopIncomingClipNotifier }
    single<HistoryArchiveStore> {
        createHistoryArchiveStore(
            dao = get<ClipboardEventDao>(),
            deviceDirectory = getOrNull<DeviceDirectory>(),
        )
    }
}
