package edu.androidsec.notes.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import edu.androidsec.notes.R
import edu.androidsec.notes.domain.Note
import java.io.File

/**
 * Экспорт заметки файлом через FileProvider (ipc-5).
 *
 * Другому приложению уходит не путь к файлу, а `content://`-ссылка и временное право на чтение
 * именно этого URI (`FLAG_GRANT_READ_URI_PERMISSION`). Провайдер не экспортирован и в
 * `file_paths.xml` открывает только `cacheDir/exports/`, поэтому больше получатель ничего
 * прочитать не может, а право исчезает, когда он закончит работу с интентом.
 */
object ShareNote {
    const val AUTHORITY = "edu.androidsec.notes.files"

    fun shareNoteAsFile(context: Context, note: Note): File {
        val file = NoteExporter.export(context, note)
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            // clipData нужен, чтобы право на чтение распространялось на URI из EXTRA_STREAM.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return file
    }
}
