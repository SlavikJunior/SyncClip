package io.github.slavikjunior.syncclip.server.feature.auth.domain

import java.time.Duration

/** Причины отзыва refresh-строк; совпадают с CHECK в `refresh_tokens`. */
internal object RefreshReasons {
    const val ROTATED = "rotated"
    const val REUSE = "reuse"
    const val LOGOUT = "logout"
    const val SUPERSEDED = "superseded"
}

/** Benign-retry окно после ротации (§7.4). */
internal val REFRESH_GRACE_WINDOW: Duration = Duration.ofSeconds(30)
