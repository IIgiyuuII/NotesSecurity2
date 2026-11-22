package edu.androidsec.notes.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import edu.androidsec.notes.R

/**
 * Копирование текста заметки в буфер обмена.
 *
 * Для секретной заметки содержимое помечается как чувствительное (`IS_SENSITIVE`): система
 * (Android 13+) не показывает его предпросмотр в подтверждении копирования, а клавиатуры
 * и менеджеры буфера не сохраняют такой текст в историю.
 */
object NoteClipboard {
    private const val LABEL = "note"

    /** Тот же ключ, что `ClipDescription.EXTRA_IS_SENSITIVE` (константа появилась в API 33). */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /** Собирает содержимое буфера; вынесено отдельно, чтобы его можно было проверить тестом. */
    fun buildClip(text: String, sensitive: Boolean): ClipData {
        val clip = ClipData.newPlainText(LABEL, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
        return clip
    }

    fun copy(context: Context, text: String, sensitive: Boolean) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(buildClip(text, sensitive))
        // С Android 13 система сама показывает подтверждение; свой тост нужен только на старых.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.note_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
