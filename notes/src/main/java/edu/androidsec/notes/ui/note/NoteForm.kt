package edu.androidsec.notes.ui.note

import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.androidsec.notes.R
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteError
import edu.androidsec.notes.domain.NoteField
import edu.androidsec.notes.domain.NoteValidator

/**
 * Фильтр ввода по принципу белого списка: если ПОСЛЕ правки текст недопустим,
 * правка целиком откатывается ([revertAllChanges]), а не «исправляется».
 *
 * Исключение: если текст УЖЕ был недопустим (например, заметка из версии 1 длиннее 5000
 * символов), разрешено только его укорачивать, иначе такую заметку нельзя было бы привести
 * в порядок. Ошибка при этом остаётся на экране.
 *
 * Фильтр вызывается только на правки пользователя (клавиатура, вставка), а не на
 * программную загрузку текста через [NoteForm.load].
 */
class RejectInvalidInput(
    private val check: (String) -> NoteError?,
    private val onError: (NoteError) -> Unit,
    private val onValid: () -> Unit,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {

        val before = originalText.toString()
        val after = asCharSequence().toString()
        // Изменился только курсор или выделение, текст прежний: проверять нечего.
        if (after == before) return
        val error = check(after)

        when {
            error == null -> onValid()
            check(before) != null && after.length < before.length -> onError(error)
            else -> {
                revertAllChanges()
                onError(error)
            }
        }
    }
}

/** Поля формы заметки и сообщения об ошибках под ними. Живёт во ViewModel. */
@Stable
class NoteForm {
    val title = TextFieldState()
    val body = TextFieldState()
    val tags = TextFieldState()

    var titleError: NoteError? by mutableStateOf(null)
        private set
    var bodyError: NoteError? by mutableStateOf(null)
        private set
    var tagsError: NoteError? by mutableStateOf(null)
        private set

    val titleFilter: InputTransformation = RejectInvalidInput(
        check = NoteValidator::checkTitle,
        onError = { titleError = it },
        onValid = { titleError = null },
    )
    val bodyFilter: InputTransformation = RejectInvalidInput(
        check = NoteValidator::checkBody,
        onError = { bodyError = it },
        onValid = { bodyError = null },
    )
    val tagsFilter: InputTransformation = RejectInvalidInput(
        check = NoteValidator::checkTagsInput,
        onError = { tagsError = it },
        onValid = { tagsError = null },
    )

    /** Заполняет поля данными заметки из базы (программная запись, фильтры не вызываются). */
    fun load(note: Note) {
        title.setTextAndPlaceCursorAtEnd(note.title)
        body.setTextAndPlaceCursorAtEnd(note.body)
        tags.setTextAndPlaceCursorAtEnd(NoteValidator.formatTags(note.tags))
        titleError = null
        bodyError = null
        tagsError = null
    }

    /** Показывает ошибки, найденные при сохранении (репозиторий отказал). */
    fun showErrors(errors: List<NoteError>) {
        titleError = errors.firstOrNull { it.field == NoteField.TITLE }
        bodyError = errors.firstOrNull { it.field == NoteField.BODY }
        tagsError = errors.firstOrNull { it.field == NoteField.TAGS }
    }

    fun toNote(id: Long, photoPath: String?): Note = Note(
        id = id,
        title = title.text.toString(),
        body = body.text.toString(),
        photoPath = photoPath,
        tags = NoteValidator.parseTags(tags.text.toString()),
    )
}

/** Текст сообщения для пользователя по причине отказа. */
@StringRes
fun NoteError.messageRes(): Int = when (this) {
    NoteError.TITLE_TOO_LONG -> R.string.error_title_too_long
    NoteError.TITLE_MULTILINE -> R.string.error_title_multiline
    NoteError.TITLE_BAD_CHARS -> R.string.error_title_bad_chars
    NoteError.BODY_TOO_LONG -> R.string.error_body_too_long
    NoteError.BODY_BAD_CHARS -> R.string.error_body_bad_chars
    NoteError.TAGS_TOO_MANY -> R.string.error_tags_too_many
    NoteError.TAG_EMPTY -> R.string.error_tag_empty
    NoteError.TAG_TOO_LONG -> R.string.error_tag_too_long
    NoteError.TAG_BAD_CHARS -> R.string.error_tag_bad_chars
}
