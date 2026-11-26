package io.github.slavikjunior.syncclip.core.clipboard

/**
 * Тексты из системного Share Sheet.
 *
 * На Android Share Activity отправляет текст сразу. На iOS расширение тоже
 * отправляет сразу (см. `ShareExtension/README.md`), но если сессии/сети нет —
 * кладёт текст в App Group. Успешно отправленные расширением тексты хранятся
 * отдельной очередью: приложение добавляет их в историю, не отправляя повторно.
 */
public interface PendingShareSource {

    /** Забирает и очищает неотправленные тексты. */
    public fun take(): List<String>

    /**
     * Тексты, уже ушедшие с сервера из расширения.
     *
     * Нужны только для локальной истории: повторная отправка создала бы
     * дубль у получателей.
     */
    public fun takeAlreadySent(): List<String> = emptyList()
}

/** Платформенная очередь; [platformContext] нужен только Android. */
public expect fun createPendingShareSource(platformContext: Any? = null): PendingShareSource
