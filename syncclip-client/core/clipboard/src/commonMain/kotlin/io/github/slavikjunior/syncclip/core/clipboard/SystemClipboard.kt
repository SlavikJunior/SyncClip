package io.github.slavikjunior.syncclip.core.clipboard

import kotlinx.coroutines.flow.Flow

/**
 * Платформенный доступ к системному буферу обмена.
 *
 * Реализация не применяет бизнес-правила: она только сообщает об изменениях и
 * выполняет чтение/запись. Эхо-защита, дедупликация и лимит размера — в
 * [ClipboardGateway], чтобы поведение было одинаковым на всех платформах.
 */
public interface SystemClipboard {

    /**
     * Изменения буфера по мере их появления.
     *
     * Поток горячий для Android (системный listener) и холодный для iOS/Desktop
     * (опрос `changeCount`/содержимого). Платформа может прислать событие и для
     * программной записи — её отбрасывает [ClipboardGateway].
     */
    public fun changes(): Flow<ClipboardChange>

    /** Текущий текст буфера или `null`, если там не текст. */
    public suspend fun read(): String?

    /** Программная запись в буфер (вставка удалённого пакета). */
    public suspend fun write(text: String)
}
