package edu.androidsec.notes.data

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDeletionTest {

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

    private fun rowCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM notes").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private suspend fun saveFull(): Long = repo.save(
        Note(
            title = "Важная заметка",
            body = "Текст, который нельзя потерять",
            tags = listOf("kotlin", "room-2"),
            isSecret = true,
            photoPath = "/data/photos/p.jpg",
        ),
    )

    @Test
    fun softDelete_hidesNoteEverywhere_butKeepsTheRow() = runBlocking {
        val id = saveFull()

        assertTrue(repo.softDelete(id))

        assertNull(repo.get(id))
        assertTrue(repo.observeAll().first().isEmpty())
        for (sort in SearchSort.entries) assertTrue(repo.search("", sort).first().isEmpty())
        assertTrue(dao.titlesBlocking().isEmpty())
        // Строка ещё в базе: заметка не потеряна, её можно вернуть.
        assertEquals(1, rowCount())
    }

    @Test
    fun undo_restoresNoteCompletely() = runBlocking {
        val id = saveFull()
        val before = repo.get(id)
        assertNotNull(before)

        repo.softDelete(id)
        assertTrue(repo.undoDelete(id))

        // Все поля на месте: заголовок, текст, теги, секретность, фото, дата изменения.
        assertEquals(before, repo.get(id))
        assertEquals(listOf(before), repo.observeAll().first())
    }

    @Test
    fun purge_removesRowForGood_andReturnsPhoto() = runBlocking {
        val id = saveFull()
        repo.softDelete(id)

        assertEquals(listOf("/data/photos/p.jpg"), repo.purgeDeleted(id))

        assertEquals(0, rowCount())
        assertFalse(repo.undoDelete(id))
    }

    @Test
    fun purge_doesNotTouchLiveNote() = runBlocking {
        val id = saveFull()

        assertTrue(repo.purgeDeleted(id).isEmpty())

        assertNotNull(repo.get(id))
        assertEquals(1, rowCount())
    }

    @Test
    fun purge_afterUndo_keepsNoteAndPhoto() = runBlocking {
        val id = saveFull()
        repo.softDelete(id)
        repo.undoDelete(id)

        // Таймер, опоздавший после «Отменить», ничего не удаляет и фото не возвращает.
        assertTrue(repo.purgeDeleted(id).isEmpty())
        assertNotNull(repo.get(id))
    }

    @Test
    fun softDelete_twice_secondIsNoOp() = runBlocking {
        val id = saveFull()
        assertTrue(repo.softDelete(id))
        assertFalse(repo.softDelete(id))
    }

    @Test
    fun softDelete_ofMissingNote_isFalse() = runBlocking {
        assertFalse(repo.softDelete(12345))
    }

    @Test
    fun purgeDeletedBefore_removesOnlyOldMarks() = runBlocking {
        fun entity(title: String, deletedAt: Long?, photo: String?) = NoteEntity(
            title = title, body = "", photoPath = photo, updatedAt = 1L, deletedAt = deletedAt,
        )
        dao.upsert(entity("остаток после убитого процесса", deletedAt = 100, photo = "/old.jpg"))
        dao.upsert(entity("свежее удаление", deletedAt = 500, photo = "/fresh.jpg"))
        dao.upsert(entity("живая", deletedAt = null, photo = "/live.jpg"))

        val photos = repo.purgeDeletedBefore(300)

        assertEquals(listOf("/old.jpg"), photos)
        assertEquals(2, rowCount()) // свежее удаление и живая остались
        assertEquals(listOf("живая"), repo.observeAll().first().map { it.title })
    }
}
