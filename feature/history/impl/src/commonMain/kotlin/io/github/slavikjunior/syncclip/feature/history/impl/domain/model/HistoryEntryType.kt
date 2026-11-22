package io.github.slavikjunior.syncclip.feature.history.impl.domain.model

/**
 * Тип содержимого записи. Определяется эвристикой по тексту и используется
 * только для отображения; БД его не хранит.
 */
internal enum class HistoryEntryType {
    TEXT,
    LINK,
    CODE,
    ;

    companion object {
        private val URL = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
        private val DOMAIN = Regex("""^[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+(/\S*)?$""")
        private val CODE_MARKERS = listOf(
            "fun ",
            "val ",
            "var ",
            "class ",
            "object ",
            "import ",
            "return ",
            "=>",
            "->",
            "{",
            "}",
            "</",
            ";\n",
        )

        fun fromText(text: String): HistoryEntryType {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return TEXT
            val singleLine = trimmed.lineSequence().count() == 1
            if (singleLine && (URL.matches(trimmed) || DOMAIN.matches(trimmed))) {
                return LINK
            }
            if (CODE_MARKERS.any { marker -> trimmed.contains(marker) }) {
                return CODE
            }
            return TEXT
        }
    }
}
