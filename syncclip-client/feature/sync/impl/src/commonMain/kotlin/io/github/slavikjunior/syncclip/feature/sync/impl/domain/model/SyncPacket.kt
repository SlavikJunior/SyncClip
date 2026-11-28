package io.github.slavikjunior.syncclip.feature.sync.impl.domain.model

/**
 * Исходящий пакет копирования.
 *
 * [eventId] присваивается один раз при захвате и переиспользуется всеми
 * транспортами: LAN и сервер доставляют один и тот же пакет, а получатель
 * дедуплицирует его по этому ID (FR-2.5).
 *
 * [targetDeviceIds] заполняется только адресной отправкой (US-016): пустой
 * список означает широковещательную доставку всем устройствам аккаунта.
 */
internal data class SyncPacket(
    val eventId: String,
    val text: String,
    val copiedAtMillis: Long,
    val isProgrammatic: Boolean = false,
    val targetDeviceIds: List<String> = emptyList(),
)
