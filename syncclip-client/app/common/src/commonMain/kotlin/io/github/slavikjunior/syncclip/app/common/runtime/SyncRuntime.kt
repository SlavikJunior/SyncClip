package io.github.slavikjunior.syncclip.app.common.runtime

import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import io.github.slavikjunior.syncclip.feature.sync.api.LanTransportStarter
import io.github.slavikjunior.syncclip.feature.sync.api.SyncTransportStarter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Фоновое ядро приложения: захват, LAN и WAN живут вне Compose.
 *
 * На Desktop окно можно скрыть в трей — композиция при этом уничтожается,
 * поэтому конвейер не должен зависеть от неё. `start` идемпотентен: точка входа
 * (Desktop `main`, Android/iOS `SyncClipApp`) вызывает его один раз, повторный
 * вызов ничего не делает, пока корутина жива.
 *
 * Сессия — единственный триггер: при выходе `collectLatest` отменяет все три
 * конвейера, при входе запускает заново.
 */
public class SyncRuntime(
    private val sessionStore: SessionStore,
    private val capture: ClipboardCaptureStarter,
    private val lan: LanTransportStarter,
    private val transport: SyncTransportStarter,
) {

    private var job: Job? = null

    /**
     * @param scope область жизни процесса. Вызывается только с главного потока,
     *   поэтому гонка двух `start` не создаёт два конвейера.
     */
    public fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            sessionStore.state.collectLatest { state ->
                if (state is SessionState.SignedIn) {
                    // supervisorScope: падение LAN (mDNS занят/нет сети) не должно
                    // останавливать захват и WAN.
                    supervisorScope {
                        launch { capture.run() }
                        launch { lan.run() }
                        launch { transport.run() }
                    }
                }
            }
        }
    }
}
