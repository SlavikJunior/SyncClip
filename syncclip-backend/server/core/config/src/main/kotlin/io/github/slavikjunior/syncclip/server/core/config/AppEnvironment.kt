package io.github.slavikjunior.syncclip.server.core.config

/** Окружение запуска: влияет на строгость валидации конфига (§5.3). */
enum class AppEnvironment {
    DEV,
    STAGING,
    PROD,
    ;

    companion object {
        fun fromWire(value: String?): AppEnvironment =
            when (value?.trim()?.lowercase()) {
                "dev", "development", null, "" -> DEV
                "staging" -> STAGING
                "prod", "production" -> PROD
                else -> throw ConfigValidationException(listOf("APP_ENV: unknown environment"))
            }
    }
}
