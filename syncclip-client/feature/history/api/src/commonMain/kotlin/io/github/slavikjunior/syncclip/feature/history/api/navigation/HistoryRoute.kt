package io.github.slavikjunior.syncclip.feature.history.api.navigation

import io.github.slavikjunior.syncclip.shared.model.NavKey
import kotlinx.serialization.Serializable

/** Ключ главного экрана истории (Navigation 3, INV-18). */
@Serializable
public data object HistoryRoute : NavKey
