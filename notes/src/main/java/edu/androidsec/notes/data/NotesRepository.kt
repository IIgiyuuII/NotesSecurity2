package edu.androidsec.notes.data

import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.SearchQuery
import edu.androidsec.notes.data.db.toDomain
import edu.androidsec.notes.data.db.toEntity
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteError
import edu.androidsec.notes.domain.NoteValidator
import edu.androidsec.notes.domain.SearchSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Заметка не прошла проверку и в базу не записана. Текст заметки в сообщение не попадает. */
class InvalidNoteException(val errors: List<NoteError>) :
    IllegalArgumentException("Note rejected: $errors")

/** Порт доступа к заметкам. ViewModel-и зависят только от него. */
interface NotesRepository {
    fun observeAll(): Flow<List<Note>>

    /**
     * Поиск по заголовку и тексту; `%`, `_` и `\` в [query] ищутся буквально.
     * Пустая строка возвращает все заметки. Список обновляется при изменении базы.
     */
    fun search(query: String, sort: SearchSort): Flow<List<Note>>

    suspend fun get(id: Long): Note?

    /**
     * Сохраняет заметку и возвращает её id (для новой заметки — только что выданный).
     * @throws InvalidNoteException если заметка нарушает правила [NoteValidator]; в базу ничего не пишется.
     */
    suspend fun save(note: Note): Long

    /**
     * Удаление в два шага (см. NoteDeleter). Шаг 1: спрятать заметку, ничего не стирая.
     * @return true, если заметка была живой и теперь спрятана.
     */
    suspend fun softDelete(id: Long): Boolean

    /** «Отменить»: вернуть спрятанную заметку целиком. @return true, если было что возвращать. */
    suspend fun undoDelete(id: Long): Boolean

    /**
     * Шаг 2: окончательно удалить спрятанную заметку. Живую не трогает.
     * @return пути фото, чьи строки реально удалены (файлы удаляет вызывающий).
     */
    suspend fun purgeDeleted(id: Long): List<String>

    /** Окончательно удалить всё, что спрятано раньше [cutoffMillis] (остатки после убитого процесса). */
    suspend fun purgeDeletedBefore(cutoffMillis: Long): List<String>
}

class RoomNotesRepository(private val dao: NoteDao) : NotesRepository {

    override fun observeAll(): Flow<List<Note>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun search(query: String, sort: SearchSort): Flow<List<Note>> {
        val pattern = SearchQuery.likePattern(query)
        // Каждый вариант сортировки — отдельный готовый запрос; `when` по enum
        // без ветки else: добавить вариант и забыть запрос компилятор не даст.
        val source = when (sort) {
            SearchSort.TITLE -> dao.searchByTitle(pattern)
            SearchSort.UPDATED_NEWEST -> dao.searchNewestFirst(pattern)
            SearchSort.UPDATED_OLDEST -> dao.searchOldestFirst(pattern)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

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

    override suspend fun softDelete(id: Long): Boolean =
        dao.markDeleted(id, System.currentTimeMillis()) > 0

    override suspend fun undoDelete(id: Long): Boolean = dao.restore(id) > 0

    override suspend fun purgeDeleted(id: Long): List<String> {
        // Сначала читаем путь фото, потом удаляем строку. Если между этими двумя шагами
        // сработала «Отменить», условие DELETE ничего не удалит, и мы вернём пустой список:
        // файл возвращённой заметки останется на месте.
        val photos = dao.deletedPhotoPaths(id)
        return if (dao.purgeDeleted(id) > 0) photos else emptyList()
    }

    override suspend fun purgeDeletedBefore(cutoffMillis: Long): List<String> {
        val photos = dao.expiredPhotoPaths(cutoffMillis)
        return if (dao.purgeExpired(cutoffMillis) > 0) photos else emptyList()
    }
}
