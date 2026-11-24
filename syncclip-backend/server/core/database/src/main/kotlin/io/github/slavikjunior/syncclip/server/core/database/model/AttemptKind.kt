package io.github.slavikjunior.syncclip.server.core.database.model

/** Виды записей auth_attempts (CHECK в DDL, CG-30). */
object AttemptKind {
    const val LOGIN = "login"
    const val MASTER_PASSWORD = "master_password"
    const val REFRESH_REUSE = "refresh_reuse"
    const val GUEST_EXCHANGE = "guest_exchange"
}
