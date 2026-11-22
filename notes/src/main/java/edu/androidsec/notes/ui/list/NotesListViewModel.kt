package edu.androidsec.notes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.delete.NoteDeleter
import edu.androidsec.notes.delete.PendingDeletion
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NotesListViewModel(
    repository: NotesRepository,
    private val deleter: NoteDeleter,
) : ViewModel() {

    /** Список заметок из Room; `null` — список ещё загружается. */
    val notes: StateFlow<List<Note>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** Последняя удалённая заметка, которую ещё можно вернуть: по ней экран показывает снекбар. */
    val pendingDeletion: StateFlow<PendingDeletion?> = deleter.pending

    /** Не удаляет сразу: прячет заметку и запускает окно отмены. */
    fun delete(id: Long) = deleter.delete(id)

    fun undoDelete(id: Long) = deleter.undo(id)
}
