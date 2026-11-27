package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthFailureReason

/** Одноразовые события экрана входа (INV-11). */
internal sealed interface AuthEffect {

    data object Authenticated : AuthEffect

    data class ShowMessage(val reason: AuthFailureReason) : AuthEffect
}
