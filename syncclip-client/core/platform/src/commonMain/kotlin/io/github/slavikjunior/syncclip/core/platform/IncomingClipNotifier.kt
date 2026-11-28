package io.github.slavikjunior.syncclip.core.platform

/**
 * Локальное уведомление о входящей копии (US-009).
 *
 * Решение «показывать или нет» принимает `:feature:sync:impl` по настройкам
 * пользователя; платформа только отображает. Android использует канал FCM,
 * остальные платформы по умолчанию молчат — на Desktop и iOS в фоне приложение
 * не получает WebSocket-кадры.
 */
public interface IncomingClipNotifier {

    public fun notifyIncoming(originDeviceName: String?)
}

/** Платформа/сборка без локальных уведомлений. */
public object NoopIncomingClipNotifier : IncomingClipNotifier {

    override fun notifyIncoming(originDeviceName: String?) = Unit
}
