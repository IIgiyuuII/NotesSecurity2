package edu.androidsec.notes.deeplink

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Что пришло во входящем интенте Activity. */
sealed interface IncomingNote {
    /** Открыть заметку с корректным id (существует ли она, проверит экран заметки). */
    data class Open(val id: Long) : IncomingNote

    /** Ссылка на заметку есть, но параметр некорректен: показать сообщение, ничего не открывать. */
    data object Invalid : IncomingNote

    /** Обычный запуск без ссылки. */
    data object None : IncomingNote
}

/**
 * Deep link `notes://note/<id>` (ipc-3).
 *
 * Данные интента приходят от любого приложения (и из браузера: у фильтра есть BROWSABLE),
 * поэтому разбираются как недоверенный ввод: принимается только схема `notes`, хост `note`
 * (без userinfo и порта) и путь из ровно одного сегмента — положительного числа.
 */
object NoteDeepLink {
    const val SCHEME = "notes"
    const val HOST = "note"

    /** `/<от 1 до 18 цифр>`: 18 цифр заведомо влезают в Long, переполнение невозможно. */
    private val PATH = Regex("/([0-9]{1,18})")

    /** Ссылка на заметку в фиксированном формате `notes://note/<id>`. */
    fun uriFor(noteId: Long): Uri = "$SCHEME://$HOST/$noteId".toUri()

    fun parse(intent: Intent?): IncomingNote {
        // Обычный запуск с лаунчера (MAIN) данных не несёт.
        if (intent == null || intent.action != Intent.ACTION_VIEW) return IncomingNote.None
        val data = intent.data ?: return IncomingNote.None

        if (!SCHEME.equals(data.scheme, ignoreCase = true)) return IncomingNote.Invalid
        // authority, а не host: так «notes://evil@note/1» и «notes://note:80/1» не проходят.
        if (!HOST.equals(data.authority, ignoreCase = true)) return IncomingNote.Invalid

        val match = PATH.matchEntire(data.encodedPath.orEmpty()) ?: return IncomingNote.Invalid
        val id = match.groupValues[1].toLongOrNull()?.takeIf { it > 0L } ?: return IncomingNote.Invalid
        return IncomingNote.Open(id)
    }
}
