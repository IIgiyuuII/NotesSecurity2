package edu.androidsec.notes.data

import android.content.ClipDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.androidsec.notes.clipboard.NoteClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteClipboardTest {

    @Test
    fun secretNote_clipIsMarkedSensitive() {
        val clip = NoteClipboard.buildClip("секретный текст", sensitive = true)

        assertEquals("секретный текст", clip.getItemAt(0).text.toString())
        // Ключ проверяется системной константой: если наш литерал разойдётся с ней, тест упадёт.
        assertTrue(clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }

    @Test
    fun ordinaryNote_clipIsNotMarked() {
        val clip = NoteClipboard.buildClip("обычный текст", sensitive = false)

        assertEquals("обычный текст", clip.getItemAt(0).text.toString())
        assertFalse(clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }
}
