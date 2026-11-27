package io.github.slavikjunior.syncclip.feature.auth.impl.data.repository

import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentity
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeSessionStore : SessionStore {

    private val current = MutableStateFlow<SessionState>(SessionState.SignedOut)
    var saved: SessionSnapshot? = null
    var cleared: Boolean = false

    override val state: StateFlow<SessionState> = current.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun save(session: SessionSnapshot) {
        saved = session
        current.value = SessionState.SignedIn(session)
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val session = (current.value as? SessionState.SignedIn)?.session ?: return
        current.value = SessionState.SignedIn(
            session.copy(accessToken = accessToken, refreshToken = refreshToken),
        )
    }

    override suspend fun clear() {
        cleared = true
        current.value = SessionState.SignedOut
    }

    override fun accessToken(): String? =
        (current.value as? SessionState.SignedIn)?.session?.accessToken
}

internal class FakeDeviceIdentityStore : DeviceIdentityStore {

    override suspend fun identity(): DeviceIdentity = DeviceIdentity(
        deviceId = "device-1",
        deviceName = "Test device",
        osType = "Android",
    )
}
