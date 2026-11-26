package io.github.slavikjunior.syncclip.core.clipboard

/** Ограничения на размер фрагмента из NFR паспорта. */
public object ClipboardLimits {

    /** Максимальный размер одного текстового фрагмента — 512 КиБ UTF-8. */
    public const val MAX_TEXT_BYTES: Int = 512 * 1024

    /** Размер строки в UTF-8 байтах — та же метрика, что и в лимите сервера. */
    public fun utf8Size(text: String): Int = text.encodeToByteArray().size
}
