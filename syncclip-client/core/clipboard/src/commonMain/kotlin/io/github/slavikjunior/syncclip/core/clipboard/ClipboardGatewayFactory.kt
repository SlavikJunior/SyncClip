package io.github.slavikjunior.syncclip.core.clipboard

/**
 * Окно, в котором системное событие с текстом собственной вставки считается
 * эхом. Системные callback'и приходят с задержкой в десятки миллисекунд, поэтому
 * окно с запасом больше 200 мс из FR-2.7.
 */
private const val DEFAULT_ECHO_WINDOW_MILLIS = 2_000L

/** Создаёт gateway с правилами паспорта поверх платформенного буфера. */
public fun createClipboardGateway(
    systemClipboard: SystemClipboard,
    clock: EpochClock = EpochClock.System,
    echoWindowMillis: Long = DEFAULT_ECHO_WINDOW_MILLIS,
    maxTextBytes: Int = ClipboardLimits.MAX_TEXT_BYTES,
): ClipboardGateway = ObservedClipboardGateway(
    systemClipboard = systemClipboard,
    clock = clock,
    echoWindowMillis = echoWindowMillis,
    maxTextBytes = maxTextBytes,
)

/** Создаёт платформенный буфер; [platformContext] нужен только Android. */
public expect fun createSystemClipboard(platformContext: Any? = null): SystemClipboard
