package edu.androidsec.notes.domain

/** К какому полю относится ошибка (экран показывает сообщение под нужным полем). */
enum class NoteField { TITLE, BODY, TAGS }

/** Причина, по которой значение не принято. Тексты для пользователя лежат в strings.xml. */
enum class NoteError(val field: NoteField) {
    TITLE_TOO_LONG(NoteField.TITLE),
    TITLE_MULTILINE(NoteField.TITLE),
    TITLE_BAD_CHARS(NoteField.TITLE),
    BODY_TOO_LONG(NoteField.BODY),
    BODY_BAD_CHARS(NoteField.BODY),
    TAGS_TOO_MANY(NoteField.TAGS),
    TAG_EMPTY(NoteField.TAGS),
    TAG_TOO_LONG(NoteField.TAGS),
    TAG_BAD_CHARS(NoteField.TAGS),
}

/**
 * Правила допустимого ввода заметки (белый список).
 *
 * Класс не зависит от Android: его вызывают и поля ввода (InputTransformation),
 * и репозиторий при сохранении, и обычные unit-тесты. Одни и те же правила в обоих
 * местах дают гарантию: что нельзя ввести на экране, нельзя и записать в обход экрана.
 *
 * Каждая функция возвращает первую найденную ошибку или null, если всё допустимо.
 */
object NoteValidator {
    const val TITLE_MAX_LENGTH = 100
    const val BODY_MAX_LENGTH = 5000
    const val TAGS_MAX_COUNT = 5
    const val TAG_MAX_LENGTH = 20
    const val TAGS_SEPARATOR = ' '

    /** Белый список символов тега: a-z, 0-9 и дефис. Никаких других, включая заглавные и кириллицу. */
    private fun isTagChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '-'

    private fun isLineBreak(c: Char): Boolean =
        c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'

    /** Длина в символах Unicode (code point), а не в UTF-16: эмодзи считается за один символ. */
    private fun length(s: String): Int = s.codePointCount(0, s.length)

    /** Заголовок: до 100 символов, одна строка, без управляющих символов. */
    fun checkTitle(title: String): NoteError? = when {
        title.any(::isLineBreak) -> NoteError.TITLE_MULTILINE
        title.any { it.isISOControl() } -> NoteError.TITLE_BAD_CHARS
        length(title) > TITLE_MAX_LENGTH -> NoteError.TITLE_TOO_LONG
        else -> null
    }

    /** Текст: до 5000 символов; из управляющих символов разрешены только перевод строки и табуляция. */
    fun checkBody(body: String): NoteError? = when {
        body.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } -> NoteError.BODY_BAD_CHARS
        length(body) > BODY_MAX_LENGTH -> NoteError.BODY_TOO_LONG
        else -> null
    }

    /**
     * Проверка строки тегов ВО ВРЕМЯ ВВОДА (так, как её видит поле).
     * Теги разделены ровно одним пробелом; один пробел в конце допустим, чтобы можно было
     * начать следующий тег, но только если пятый тег ещё не набран.
     */
    fun checkTagsInput(text: String): NoteError? {
        if (text.isEmpty()) return null
        if (!text.all { it == TAGS_SEPARATOR || isTagChar(it) }) return NoteError.TAG_BAD_CHARS

        val parts = text.split(TAGS_SEPARATOR)
        val last = parts.lastIndex
        var count = 0
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty()) {
                // Пустая часть допустима только как один пробел в самом конце (и не в начале).
                if (index != last || index == 0) return NoteError.TAG_EMPTY
            } else {
                if (part.length > TAG_MAX_LENGTH) return NoteError.TAG_TOO_LONG
                count++
            }
        }
        if (count > TAGS_MAX_COUNT) return NoteError.TAGS_TOO_MANY
        if (parts.last().isEmpty() && count >= TAGS_MAX_COUNT) return NoteError.TAGS_TOO_MANY
        return null
    }

    /** Проверка готового списка тегов ПРИ СОХРАНЕНИИ. */
    fun checkTags(tags: List<String>): NoteError? {
        if (tags.size > TAGS_MAX_COUNT) return NoteError.TAGS_TOO_MANY
        for (tag in tags) {
            if (tag.isEmpty()) return NoteError.TAG_EMPTY
            if (!tag.all(::isTagChar)) return NoteError.TAG_BAD_CHARS
            if (tag.length > TAG_MAX_LENGTH) return NoteError.TAG_TOO_LONG
        }
        return null
    }

    /** Все ошибки заметки сразу (по одной на поле). Пустой список — заметка допустима. */
    fun validate(title: String, body: String, tags: List<String>): List<NoteError> =
        listOfNotNull(checkTitle(title), checkBody(body), checkTags(tags))

    /** «kotlin room-2» -> [kotlin, room-2]. Вызывать после checkTagsInput: лишние пробелы отбрасываются. */
    fun parseTags(text: String): List<String> =
        text.split(TAGS_SEPARATOR).filter { it.isNotEmpty() }

    /** [kotlin, room-2] -> «kotlin room-2» (так теги хранятся в базе и показываются в поле). */
    fun formatTags(tags: List<String>): String = tags.joinToString(TAGS_SEPARATOR.toString())
}
