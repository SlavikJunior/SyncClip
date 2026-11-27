package io.github.slavikjunior.syncclip.feature.auth.api.navigation

import io.github.slavikjunior.syncclip.shared.model.NavKey
import kotlinx.serialization.Serializable

/** Ключ экрана входа/регистрации (Navigation 3, INV-18). */
@Serializable
public data object AuthRoute : NavKey
