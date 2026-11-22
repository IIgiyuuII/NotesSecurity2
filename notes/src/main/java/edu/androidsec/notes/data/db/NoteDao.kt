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
 * можно подбирать скрытый текст.
 */
private const val SEARCH_FILTER =
    "deleted_at IS NULL AND (" +
        "title LIKE :pattern ESCAPE '\\' OR " +
        "(is_secret = 0 AND body LIKE :pattern ESCAPE '\\'))"

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Три варианта сортировки поиска: у каждого свой готовый запрос, без RawQuery
    // и без подстановки имени колонки или направления из строки.

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
    @Query("SELECT id, title FROM notes ORDER BY id")
    fun titlesBlocking(): List<NoteTitle>
}
