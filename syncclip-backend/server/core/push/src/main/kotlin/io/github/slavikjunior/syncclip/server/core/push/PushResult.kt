package io.github.slavikjunior.syncclip.server.core.push

/** Итог отправки; `invalidTokens` удаляются из БД (§11.3). */
data class PushResult(
    val delivered: Int,
    val invalidTokens: List<String> = emptyList(),
    val retryable: Boolean = false,
)
