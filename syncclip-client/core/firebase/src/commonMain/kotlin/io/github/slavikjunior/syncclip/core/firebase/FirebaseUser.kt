package io.github.slavikjunior.syncclip.core.firebase

/** Пользователь Firebase Auth в терминах приложения (без платформенных типов). */
data class FirebaseUser(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false,
)
