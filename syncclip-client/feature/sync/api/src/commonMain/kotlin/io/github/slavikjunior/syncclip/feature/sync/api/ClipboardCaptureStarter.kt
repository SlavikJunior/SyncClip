package io.github.slavikjunior.syncclip.feature.sync.api

/**
 * Публичный вход в конвейер захвата буфера (INV-27).
 *
 * Composition root запускает [run] в области жизни приложения после входа:
 * корутина отменяется вместе с сессией, а не живёт глобально (INV-21).
 */
public interface ClipboardCaptureStarter {

    /**
     * Захватывает локальные копирования, сохраняет их в историю и публикует
     * исходящие пакеты. Возвращается только при отмене корутины.
     */
    public suspend fun run()

    /**
     * Принять текущее содержимое системного буфера как локальное копирование.
     *
     * Нужно платформенным точкам входа: iOS показывает кнопку «Отправить из
     * буфера» (FR-3.3), Android — действие из Share Sheet/плитки (FR-3.2).
     *
     * @return `true`, если текст принят и сохранён.
     */
    public suspend fun captureCurrent(): Boolean

    /**
     * Принять текст из внешней точки входа (Share Sheet): он не обязан лежать
     * в системном буфере, поэтому передаётся явно.
     *
     * @return `true`, если текст принят и сохранён.
     */
    public suspend fun captureText(text: String): Boolean
}
