package io.github.slavikjunior.syncclip.core.di

/**
 * Адрес API по умолчанию для локальной разработки.
 *
 * Android-эмулятор видит хост как `10.0.2.2`, iOS-симулятор и Desktop — как
 * `localhost`. В production адрес передаётся через `named("apiBaseUrl")`.
 */
internal expect fun defaultApiBaseUrl(): String
