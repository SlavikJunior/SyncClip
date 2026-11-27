package io.github.slavikjunior.syncclip.app.common.runtime

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Запросы от платформенных точек входа к UI.
 *
 * Трей и глобальный хоткей должны открыть историю, но не знают о Compose-навигации.
 * Replay=1: запрос, отправленный при скрытом окне, доедет до новой композиции.
 */
public class AppSectionRequests {

    private val requests = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    public val history: SharedFlow<Unit> = requests.asSharedFlow()

    public fun openHistory() {
        requests.tryEmit(Unit)
    }
}
