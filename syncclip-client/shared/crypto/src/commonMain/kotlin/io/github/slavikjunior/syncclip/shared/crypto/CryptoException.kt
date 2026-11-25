package io.github.slavikjunior.syncclip.shared.crypto

/** Ошибка шифрования/расшифровки: неверный ключ, повреждённый пакет, битый тег. */
public class CryptoException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
