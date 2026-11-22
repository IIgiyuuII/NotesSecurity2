package edu.androidsec.notes.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.NoteEntity
import edu.androidsec.notes.data.db.NotesDatabase
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.SearchSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteSearchTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NotesDatabase
    private lateinit var dao: NoteDao
    private lateinit var repo: RoomNotesRepository

    @Before
    fun setUp() {
        // In-memory база: настоящие данные приложения тесты не трогают.
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        dao = db.noteDao()
        repo = RoomNotesRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun add(
        title: String,
        body: String = "",
        updatedAt: Long = 1L,
        secret: Boolean = false,
        deletedAt: Long? = null,
    ): Long = dao.upsert(
        NoteEntity(
            title = title,
            body = body,
            photoPath = null,
            updatedAt = updatedAt,
            isSecret = secret,
            deletedAt = deletedAt,
        ),
    )

    private suspend fun found(query: String, sort: SearchSort = SearchSort.TITLE): List<String> =
        repo.search(query, sort).first().map { it.title }

    // ---------- что ищем ----------

    @Test
    fun emptyQuery_returnsAllLiveNotes() = runBlocking {
        add("one")
        add("two")
        assertEquals(listOf("one", "two"), found(""))
    }

    @Test
    fun searchesInTitleAndInBody() = runBlocking {
        add("Молоко", body = "в магазин")
        add("Работа", body = "купить хлеб")
        add("Другое", body = "ничего")
        assertEquals(listOf("Работа"), found("купить"))
        assertEquals(listOf("Молоко"), found("Молоко"))
        assertEquals(listOf("Молоко"), found("магазин"))
    }

    // ---------- %, _ и \ ищутся буквально ----------

    @Test
    fun percent_isLiteral() = runBlocking {
        add("скидка 50% сегодня")
        add("скидка 50 рублей")
        assertEquals(listOf("скидка 50% сегодня"), found("%"))
        assertEquals(listOf("скидка 50% сегодня"), found("50%"))
    }

    @Test
    fun underscore_isLiteral() = runBlocking {
        add("a_b")
        add("axb")
        assertEquals(listOf("a_b"), found("_"))
        assertEquals(listOf("a_b"), found("a_b"))
    }

    @Test
    fun backslash_isLiteral() = runBlocking {
        add("путь\\к файлу")
        add("путь к файлу")
        assertEquals(listOf("путь\\к файлу"), found("\\"))
    }

    @Test
    fun backslashFollowedByPercent_isLiteral() = runBlocking {
        add("a\\%b")
        add("a%b")
        add("a\\b")
        assertEquals(listOf("a\\%b"), found("\\%"))
    }

    // ---------- запрос — данные, а не SQL ----------

    @Test
    fun sqlLikeInput_isTreatedAsPlainText() = runBlocking {
        add("обычная заметка")
        assertTrue(found("' OR 1=1 --").isEmpty())
        assertTrue(found("x%'; DROP TABLE notes; --").isEmpty())
        // Таблица цела и заметка на месте.
        assertEquals(listOf("обычная заметка"), found(""))
    }

    // ---------- сортировка ----------

    @Test
    fun sortByTitle() = runBlocking {
        add("banana")
        add("Apple")
        add("cherry")
        assertEquals(listOf("Apple", "banana", "cherry"), found("", SearchSort.TITLE))
    }

    @Test
    fun sortByDate_newestFirst() = runBlocking {
        add("old", updatedAt = 100)
        add("new", updatedAt = 300)
        add("mid", updatedAt = 200)
        assertEquals(listOf("new", "mid", "old"), found("", SearchSort.UPDATED_NEWEST))
    }

    @Test
    fun sortByDate_oldestFirst() = runBlocking {
        add("old", updatedAt = 100)
        add("new", updatedAt = 300)
        add("mid", updatedAt = 200)
        assertEquals(listOf("old", "mid", "new"), found("", SearchSort.UPDATED_OLDEST))
    }

    // ---------- удалённые и секретные ----------

    @Test
    fun softDeletedNotes_areNotFound() = runBlocking {
        add("живая")
        add("удалённая", deletedAt = 5L)
        assertEquals(listOf("живая"), found(""))
    }

    @Test
    fun secretNote_isFoundByTitle_butNotByBody() = runBlocking {
        add("Пароль от банка", body = "hunter2", secret = true)
        assertEquals(listOf("Пароль от банка"), found("банка"))
        assertTrue(found("hunter2").isEmpty())
    }

    // ---------- проверка в репозитории ----------

    @Test
    fun save_rejectsInvalidNotes_andWritesNothing() = runBlocking {
        val invalid = listOf(
            Note(title = "a".repeat(101)),
            Note(title = "строка 1\nстрока 2"),
            Note(title = "ok", body = "b".repeat(5001)),
            Note(title = "ok", tags = listOf("a", "b", "c", "d", "e", "f")),
            Note(title = "ok", tags = listOf("BadTag")),
            Note(title = "ok", tags = listOf("a".repeat(21))),
            Note(title = "ok", tags = listOf("")),
        )
        for (note in invalid) {
            try {
                repo.save(note)
                fail("ожидали InvalidNoteException для $note")
            } catch (expected: InvalidNoteException) {
                assertFalse(expected.errors.isEmpty())
            }
        }
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun save_acceptsValidNote_withTags() = runBlocking {
        val id = repo.save(Note(title = "ok", body = "text", tags = listOf("kotlin", "room-2")))
        val saved = repo.get(id)
        assertNotNull(saved)
        assertEquals(listOf("kotlin", "room-2"), saved!!.tags)
    }

    // ---------- миграция 1 → 2 ----------

    @Test
    fun migration_1_2_keepsExistingNotes() {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        // Настоящий файл базы версии 1: так выглядела схема до ЛР3.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                "CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, body TEXT NOT NULL, photo_path TEXT, updated_at INTEGER NOT NULL)",
            )
            old.execSQL(
                "INSERT INTO notes (id, title, body, photo_path, updated_at) " +
                    "VALUES (7, 'Старая', 'Текст до обновления', NULL, 123)",
            )
            old.version = 1
        }

        val migrated = Room.databaseBuilder(context, NotesDatabase::class.java, name)
            .addMigrations(NotesDatabase.MIGRATION_1_2)
            .build()
        try {
            runBlocking {
                val note = migrated.noteDao().getById(7)
                assertNotNull("заметка пропала после миграции", note)
                assertEquals("Старая", note!!.title)
                assertEquals("Текст до обновления", note.body)
                assertEquals(123L, note.updatedAt)
                // Новые колонки получили значения по умолчанию.
                assertEquals("", note.tags)
                assertFalse(note.isSecret)
                assertNull(note.deletedAt)
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
