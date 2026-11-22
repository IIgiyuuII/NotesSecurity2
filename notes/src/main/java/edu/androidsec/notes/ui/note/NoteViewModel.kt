package edu.androidsec.notes.ui.note

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.R
import edu.androidsec.notes.data.InvalidNoteException
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.delete.NoteDeleter
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.reminder.ReminderScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Всё, что экран заметки рисует, кроме текстовых полей (они в [NoteForm]).
 * `noteId == 0` — новая, ещё не сохранённая заметка.
 */
data class NoteUiState(
    val noteId: Long,
    val isLoading: Boolean = true,
    val photoPath: String? = null,
    /** Заметки с таким id нет (например, deep link на удалённую): редактировать нечего. */
    val notFound: Boolean = false,
    /** Секретная заметка: экран защищён от скриншотов, текст не попадает в список и уведомления. */
    val isSecret: Boolean = false,
) {
    val isNew: Boolean get() = noteId == 0L
}

/** Одноразовые события для экрана: показать сообщение или закрыть экран. */
sealed interface NoteEvent {
    data class Message(@param:StringRes val resId: Int) : NoteEvent
    data object Close : NoteEvent
}

class NoteViewModel(
    noteId: Long,
    private val repository: NotesRepository,
    private val photoStore: PhotoStore,
    private val reminderScheduler: ReminderScheduler,
    private val deleter: NoteDeleter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState(noteId = noteId))
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    /** Поля ввода с фильтрами и сообщениями об ошибках. */
    val form = NoteForm()

    private val _events = Channel<NoteEvent>(Channel.BUFFERED)
    val events: Flow<NoteEvent> = _events.receiveAsFlow()

    init {
        load(noteId)
    }

    private fun load(noteId: Long) {
        if (noteId == 0L) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            val note = repository.get(noteId)
            if (note == null) {
                // ipc-3: несуществующий id не открывает пустой редактор, иначе «Сохранить»
                // создало бы запись с чужим id. Экран покажет «Заметка не найдена».
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            form.load(note)
            _uiState.update { it.copy(isLoading = false, photoPath = note.photoPath, isSecret = note.isSecret) }
        }
    }

    /** Заметка в том виде, как её сейчас видит пользователь (для сохранения и экспорта). */
    fun currentNote(): Note {
        val state = _uiState.value
        return form.toNote(state.noteId, state.photoPath, state.isSecret)
    }

    fun save() {
        if (_uiState.value.notFound) return
        viewModelScope.launch {
            try {
                val id = repository.save(currentNote())
                _uiState.update { it.copy(noteId = id) }
                _events.send(NoteEvent.Message(R.string.note_saved))
            } catch (e: InvalidNoteException) {
                // Репозиторий отказал: в базу ничего не записано. Причины показываем под полями.
                Log.w(TAG, "Note rejected: ${e.errors}")
                form.showErrors(e.errors)
                _events.send(NoteEvent.Message(R.string.note_invalid))
            }
        }
    }

    /**
     * Удаление не мгновенное: [NoteDeleter] прячет заметку и даёт 5 секунд на отмену, а экран
     * закрывается; снекбар «Заметка удалена / Отменить» покажет список. Несохранённую
     * (новую) заметку удалять нечем: просто закрываем экран.
     */
    fun delete() {
        val state = _uiState.value
        if (!state.isNew && !state.notFound) deleter.delete(state.noteId)
        _events.trySend(NoteEvent.Close)
    }

    /** Выбор фото вернул `content://`-URI: копируем файл к себе, право на чтение URI временное. */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching { photoStore.copyToPrivateStorage(uri) }
                .onSuccess { path -> _uiState.update { it.copy(photoPath = path) } }
                .onFailure {
                    Log.w(TAG, "Cannot store photo", it)
                    _events.send(NoteEvent.Message(R.string.note_photo_failed))
                }
        }
    }

    fun removePhoto() = _uiState.update { it.copy(photoPath = null) }
    fun onSecretChange(value: Boolean) = _uiState.update { it.copy(isSecret = value) }

    /** Вызывается экраном, когда разрешение на уведомления уже получено. */
    fun scheduleReminder() {
        val state = _uiState.value
        if (state.isNew || state.notFound) return
        reminderScheduler.schedule(state.noteId, form.title.text.toString(), state.isSecret)
        _events.trySend(NoteEvent.Message(R.string.reminder_scheduled))
    }

    private companion object {
        const val TAG = "NoteViewModel"
    }
}
