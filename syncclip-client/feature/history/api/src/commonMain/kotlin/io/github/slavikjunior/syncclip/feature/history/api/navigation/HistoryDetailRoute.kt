package io.github.slavikjunior.syncclip.feature.history.api.navigation

import io.github.slavikjunior.syncclip.shared.model.NavKey
import kotlinx.serialization.Serializable

/** В аргументах только ID: модель загружается в получателе (INV-18). */
@Serializable
public data class HistoryDetailRoute(val eventId: String) : NavKey
