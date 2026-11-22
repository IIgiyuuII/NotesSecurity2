package edu.androidsec.notes.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import edu.androidsec.notes.domain.NoteTitle
import kotlinx.coroutines.flow.Flow

/**
 * Условие поиска, общее для трёх запросов. Значение :pattern всегда приходит параметром
 * (см. [SearchQuery.likePattern]); `\` в SQL-тексте — символ экранирования для LIKE.
 * У секретных заметок ищем только по заголовку: иначе по результатам поиска
 * можно подбирать скрытый текст. Заметки с меткой удаления (ждут конца окна отмены) не ищутся.
 */
private const val SEARCH_FILTER =
    "deleted_at IS NULL AND (" +
        "title LIKE :pattern ESCAPE '\\' OR " +
        "(is_secret = 0 AND body LIKE :pattern ESCAPE '\\'))"

@Dao
interface NoteDao {

    // Везде, где заметки показываются или открываются, помеченные удалёнными скрыты.

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    // ---------- удаление с отменой ----------

    /** Шаг 1: спрятать заметку (одна атомарная запись). Возвращает число изменённых строк. */
    @Query("UPDATE notes SET deleted_at = :at WHERE id = :id AND deleted_at IS NULL")
    suspend fun markDeleted(id: Long, at: Long): Int

    /** «Отменить»: вернуть спрятанную заметку, остальные поля не тронуты. */
    @Query("UPDATE notes SET deleted_at = NULL WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun restore(id: Long): Int

    /** Шаг 2: фото спрятанной заметки (чтобы удалить файл вместе со строкой). */
    @Query("SELECT photo_path FROM notes WHERE id = :id AND deleted_at IS NOT NULL AND photo_path IS NOT NULL")
    suspend fun deletedPhotoPaths(id: Long): List<String>

    /** Шаг 2: окончательное удаление. Живую заметку это условие не затронет. */
    @Query("DELETE FROM notes WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun purgeDeleted(id: Long): Int

    /** Остатки после убитого процесса: помеченные раньше момента [cutoff]. */
    @Query("SELECT photo_path FROM notes WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff AND photo_path IS NOT NULL")
    suspend fun expiredPhotoPaths(cutoff: Long): List<String>

    @Query("DELETE FROM notes WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeExpired(cutoff: Long): Int

    // ---------- поиск: три варианта сортировки, у каждого свой готовый запрос ----------

    @Query("SELECT * FROM notes WHERE $SEARCH_FILTER ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun searchByTitle(pattern: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE $SEARCH_FILTER ORDER BY updated_at DESC, id DESC")
    fun searchNewestFirst(pattern: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE $SEARCH_FILTER ORDER BY updated_at ASC, id ASC")
    fun searchOldestFirst(pattern: String): Flow<List<NoteEntity>>

    /**
     * Синхронный запрос заголовков: вызывается из `ContentProvider.query()`,
     * который работает в binder-потоке, а не в главном.
     */
    @Query("SELECT id, title FROM notes WHERE deleted_at IS NULL ORDER BY id")
    fun titlesBlocking(): List<NoteTitle>
}
