package io.github.slavikjunior.syncclip.server.core.push

/** Уведомление: текст + машинные данные; клиент сам решает, показывать ли (FR-7.1). */
data class PushMessage(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val collapseKey: String? = null,
)
