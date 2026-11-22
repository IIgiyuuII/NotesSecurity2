package edu.androidsec.notes.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.NotesDatabase
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretNotesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NotesDatabase
    private lateinit var dao: NoteDao
    private lateinit var repo: RoomNotesRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        dao = db.noteDao()
        repo = RoomNotesRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun secretFlagAndTags_surviveSaveAndLoad() = runBlocking {
        val id = repo.save(Note(title = "Пароли", body = "hunter2", tags = listOf("bank", "pin-1"), isSecret = true))

        val loaded = repo.get(id)
        assertNotNull(loaded)
        assertTrue(loaded!!.isSecret)
        assertEquals(listOf("bank", "pin-1"), loaded.tags)
        assertEquals("hunter2", loaded.body)
    }

    @Test
    fun ordinaryNote_isNotSecretByDefault() = runBlocking {
        val id = repo.save(Note(title = "Обычная"))
        assertFalse(repo.get(id)!!.isSecret)
    }

    @Test
    fun titlesProvider_doesNotExposeSecretOrDeletedNotes() = runBlocking {
        repo.save(Note(title = "Открытая"))
        repo.save(Note(title = "Секретная", isSecret = true))
        val deletedId = repo.save(Note(title = "Удалённая"))
        repo.softDelete(deletedId)

        // Именно этот запрос вызывает ContentProvider для клиентского приложения.
        assertEquals(listOf("Открытая"), dao.titlesBlocking().map { it.title })
    }
}
