package io.github.slavikjunior.syncclip.feature.sync.impl.domain.model

/**
 * Эвристика «похоже на пароль или токен» (US-021).
 *
 * Правила из спецификации, все три обязательны:
 * 1. длина меньше [MAX_SENSITIVE_LENGTH] символов;
 * 2. минимум [MIN_SPECIAL_CHARS] специальных символа;
 * 3. нет пробельных символов.
 *
 * Правила намеренно консервативные: ложное срабатывание удаляет полезную запись
 * через пять минут, поэтому обычный текст с восклицательным знаком не попадает
 * под автоочистку.
 */
internal object SensitiveContentDetector {

    private const val MAX_SENSITIVE_LENGTH = 50
    private const val MIN_SPECIAL_CHARS = 2

    /** Срок жизни чувствительной записи: 5 минут из спецификации. */
    const val SENSITIVE_TTL_MILLIS: Long = 5L * 60L * 1000L

    private val specialChars: Set<Char> =
        "!@#$%^&*()_+-=[]{};:'\"\\|,<.>/?~`".toSet()

    fun isSensitive(text: String): Boolean {
        val candidate = text.trim()
        if (candidate.isEmpty() || candidate.length >= MAX_SENSITIVE_LENGTH) return false
        if (candidate.any { it.isWhitespace() }) return false
        return candidate.count { it in specialChars } >= MIN_SPECIAL_CHARS
    }
}
