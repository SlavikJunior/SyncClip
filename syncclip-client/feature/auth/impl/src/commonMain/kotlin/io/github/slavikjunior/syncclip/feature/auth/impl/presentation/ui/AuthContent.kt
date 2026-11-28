package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.preview.FormFactorPreviews
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state.AuthIntent
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state.AuthMode
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state.AuthUiState
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.auth.impl.generated.resources.Res
import syncclip.feature.auth.impl.generated.resources.auth_email
import syncclip.feature.auth.impl.generated.resources.auth_password
import syncclip.feature.auth.impl.generated.resources.auth_sign_in
import syncclip.feature.auth.impl.generated.resources.auth_sign_up
import syncclip.feature.auth.impl.generated.resources.auth_subtitle
import syncclip.feature.auth.impl.generated.resources.auth_title
import syncclip.feature.auth.impl.generated.resources.auth_toggle_to_sign_in
import syncclip.feature.auth.impl.generated.resources.auth_toggle_to_sign_up

/** Stateless-форма входа: состояние приходит сверху, события уходят вниз (INV-12). */
@Composable
internal fun AuthContent(
    state: AuthUiState,
    onIntent: (AuthIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // safeDrawing: форма по центру и снекбар не уходят под системные панели
    // и вырез экрана (на десктопе инсеты нулевые).
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.auth_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(Res.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = { onIntent(AuthIntent.EmailChanged(it)) },
                label = { Text(stringResource(Res.string.auth_email)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
                label = { Text(stringResource(Res.string.auth_password)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            state.failure?.let { failure ->
                Text(
                    text = stringResource(failure.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { onIntent(AuthIntent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(
                            when (state.mode) {
                                AuthMode.SIGN_IN -> Res.string.auth_sign_in
                                AuthMode.SIGN_UP -> Res.string.auth_sign_up
                            },
                        ),
                    )
                }
            }

            TextButton(
                onClick = { onIntent(AuthIntent.ToggleMode) },
                enabled = !state.isSubmitting,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(
                        when (state.mode) {
                            AuthMode.SIGN_IN -> Res.string.auth_toggle_to_sign_up
                            AuthMode.SIGN_UP -> Res.string.auth_toggle_to_sign_in
                        },
                    ),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@FormFactorPreviews
@Composable
private fun AuthContentPreview() {
    SyncClipTheme {
        AuthContent(
            state = AuthUiState(email = "user@example.com", password = "password"),
            onIntent = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
