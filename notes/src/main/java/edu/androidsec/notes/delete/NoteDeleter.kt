package edu.androidsec.notes.delete

import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Заметка, которую ещё можно вернуть. [purgeAtElapsedMillis] — момент окончательного удаления (SystemClock). */
data class PendingDeletion(val noteId: Long, val purgeAtElapsedMillis: Long)

/**
 * Удаление с отменой. Живёт на уровне приложения (а не экрана), поэтому таймер не пропадает
 * при уходе с экрана заметки.
 *
 * 1. [delete]: заметка прячется (одна атомарная запись в базе), запускается таймер.
 * 2. Через [UNDO_WINDOW_MILLIS] строка и файл фото удаляются окончательно.
 * 3. [undo] до этого момента отменяет таймер и возвращает заметку целиком.
 *
 * Если процесс убили в окне отмены, спрятанная строка остаётся в базе целиком; при следующем
 * запуске [purgeLeftovers] удаляет её окончательно, вместе с файлом. Заметка не бывает
 * «наполовину удалённой»: до удаления строки файл фото на месте.
 *
 * Кто первым достал задачу из [jobs], тот и владеет заметкой: [undo] и таймер не могут
 * выполниться оба.
 */
class NoteDeleter(
    private val context: Context,
    private val repository: NotesRepository,
    private val scope: CoroutineScope,
    private val photoStore: PhotoStore,
    private val reminderScheduler: ReminderScheduler,
) {
    private val jobs = HashMap<Long, Job>()

    private val _pending = MutableStateFlow<PendingDeletion?>(null)

    /** Последняя удалённая заметка, которую ещё можно вернуть (для снекбара). `null` — нечего возвращать. */
    val pending: StateFlow<PendingDeletion?> = _pending.asStateFlow()

    fun delete(noteId: Long) {
        scope.launch {
            if (!repository.softDelete(noteId)) return@launch
            val purgeAt = SystemClock.elapsedRealtime() + UNDO_WINDOW_MILLIS
            synchronized(jobs) {
                jobs[noteId] = scope.launch {
                    delay(UNDO_WINDOW_MILLIS)
                    purge(noteId)
                }
            }
            _pending.value = PendingDeletion(noteId, purgeAt)
        }
    }

    fun undo(noteId: Long) {
        scope.launch {
            // Не нашли задачу: таймер уже сработал и владеет заметкой, возвращать поздно.
            val job = synchronized(jobs) { jobs.remove(noteId) } ?: return@launch
            job.cancel()
            repository.undoDelete(noteId)
            clearPending(noteId)
        }
    }

    private suspend fun purge(noteId: Long) {
        // Нет в таблице: её забрал undo, заметку удалять нельзя.
        synchronized(jobs) { jobs.remove(noteId) } ?: return
        val photos = repository.purgeDeleted(noteId)
        reminderScheduler.cancel(noteId)
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
        photos.forEach { photoStore.delete(it) }
        clearPending(noteId)
    }

    /**
     * Вызывается при старте приложения: удаляет заметки, которые были спрятаны, но не успели
     * удалиться окончательно (процесс убили в окне отмены). Трогает только то, что спрятано
     * раньше этого вызова, поэтому свежее удаление пользователя не заденет.
     */
    suspend fun purgeLeftovers() {
        val photos = repository.purgeDeletedBefore(System.currentTimeMillis())
        photos.forEach { photoStore.delete(it) }
    }

    private fun clearPending(noteId: Long) {
        _pending.update { if (it?.noteId == noteId) null else it }
    }

    companion object {
        /** Сколько секунд можно отменить удаление. */
        const val UNDO_WINDOW_MILLIS = 5_000L
    }
}
