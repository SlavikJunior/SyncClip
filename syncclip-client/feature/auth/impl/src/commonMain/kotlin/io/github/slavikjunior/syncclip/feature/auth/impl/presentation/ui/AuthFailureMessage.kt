package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.ui

import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthFailureReason
import org.jetbrains.compose.resources.StringResource
import syncclip.feature.auth.impl.generated.resources.Res
import syncclip.feature.auth.impl.generated.resources.auth_error_email_already_in_use
import syncclip.feature.auth.impl.generated.resources.auth_error_email_not_verified
import syncclip.feature.auth.impl.generated.resources.auth_error_invalid_credentials
import syncclip.feature.auth.impl.generated.resources.auth_error_invalid_email
import syncclip.feature.auth.impl.generated.resources.auth_error_invalid_input
import syncclip.feature.auth.impl.generated.resources.auth_error_network
import syncclip.feature.auth.impl.generated.resources.auth_error_not_supported
import syncclip.feature.auth.impl.generated.resources.auth_error_unknown
import syncclip.feature.auth.impl.generated.resources.auth_error_weak_password

/** Типизированный отказ → строка ресурса (INV-14). */
internal fun AuthFailureReason.messageRes(): StringResource = when (this) {
    AuthFailureReason.INVALID_CREDENTIALS -> Res.string.auth_error_invalid_credentials
    AuthFailureReason.EMAIL_ALREADY_IN_USE -> Res.string.auth_error_email_already_in_use
    AuthFailureReason.WEAK_PASSWORD -> Res.string.auth_error_weak_password
    AuthFailureReason.INVALID_EMAIL -> Res.string.auth_error_invalid_email
    AuthFailureReason.INVALID_INPUT -> Res.string.auth_error_invalid_input
    AuthFailureReason.EMAIL_NOT_VERIFIED -> Res.string.auth_error_email_not_verified
    AuthFailureReason.NETWORK -> Res.string.auth_error_network
    AuthFailureReason.NOT_SUPPORTED -> Res.string.auth_error_not_supported
    AuthFailureReason.UNKNOWN -> Res.string.auth_error_unknown
}
