package io.github.slavikjunior.syncclip.server.core.config

/** Fail-fast ошибка конфигурации: печатает имя переменной и правило, не значение. */
class ConfigValidationException(val problems: List<String>) :
    IllegalStateException("Invalid configuration: ${problems.joinToString("; ")}")
