package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class UnsupportedPushTokenSource : PushTokenSource {

    override val tokens: Flow<String> = emptyFlow()

    override suspend fun currentToken(): String? = null

    override suspend fun requestPermission(): Boolean = false
}
