package edu.androidsec.notes.data

import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.toDomain
import edu.androidsec.notes.data.db.toEntity
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteError
import edu.androidsec.notes.domain.NoteValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Заметка не прошла проверку и в базу не записана. Текст заметки в сообщение не попадает. */
class InvalidNoteException(val errors: List<NoteError>) :
    IllegalArgumentException("Note rejected: $errors")

/** Порт доступа к заметкам. ViewModel-и зависят только от него. */
interface NotesRepository {
    fun observeAll(): Flow<List<Note>>
    suspend fun get(id: Long): Note?

    /**
     * Сохраняет заметку и возвращает её id (для новой заметки — только что выданный).
     * @throws InvalidNoteException если заметка нарушает правила [NoteValidator]; в базу ничего не пишется.
     */
    suspend fun save(note: Note): Long
    suspend fun delete(id: Long)
}

class RoomNotesRepository(private val dao: NoteDao) : NotesRepository {

    override fun observeAll(): Flow<List<Note>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Note? = dao.getById(id)?.toDomain()

    override suspend fun save(note: Note): Long {
        // Те же правила, что и в полях ввода, но проверяются ещё раз здесь: экран можно обойти
        // (другой код, баг, будущий импорт), а в базу некорректные данные попасть не должны.
        val errors = NoteValidator.validate(note.title, note.body, note.tags)
        if (errors.isNotEmpty()) throw InvalidNoteException(errors)

        val stamped = note.copy(updatedAt = System.currentTimeMillis())
        val rowId = dao.upsert(stamped.toEntity())
        return if (note.isNew) rowId else note.id
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
