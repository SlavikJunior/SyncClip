package io.github.slavikjunior.syncclip.core.datastore

/** Состояние сессии: загрузка из DataStore, гость или авторизованный пользователь. */
sealed interface SessionState {

    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val session: SessionSnapshot) : SessionState
}
