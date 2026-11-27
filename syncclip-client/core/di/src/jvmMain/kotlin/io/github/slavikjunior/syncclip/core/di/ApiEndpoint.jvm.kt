package io.github.slavikjunior.syncclip.core.di

/**
 * Desktop по умолчанию ходит в локальный сервер; `SYNCCLIP_API_BASE_URL`
 * позволяет направить клиент на другой адрес без пересборки (ручная проверка,
 * второй инстанс сервера на свободном порту).
 */
internal actual fun defaultApiBaseUrl(): String =
    System.getenv("SYNCCLIP_API_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "http://localhost:8080"
