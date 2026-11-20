package edu.androidsec.notes.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.provider.BaseColumns
import android.util.Log
import edu.androidsec.notes.appContainer

/**
 * Провайдер заголовков «для своих» (ipc-6).
 *
 * Защита лежит в манифесте: `android:permission` с signature-разрешением [PERMISSION] закрывает
 * и чтение, и запись, так что система отклоняет чужой вызов (включая `adb shell content`) ещё до
 * входа в этот класс. В коде остаётся минимум: только путь `/notes`, только колонки `_id` и
 * `title`, никакой записи.
 */
class TitlesProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, PATH_NOTES, CODE_NOTES)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        require(matcher.match(uri) == CODE_NOTES) { "Unknown URI: $uri" }

        // Кто спрашивает: полезно для отладки и для защиты.
        Log.i(TAG, "query from uid=${Binder.getCallingUid()} package=$callingPackage")

        // Отдаём только _id и title; запрос чего-либо ещё — ошибка, а не молчаливое игнорирование.
        val columns = projection?.map { it } ?: ALLOWED_COLUMNS
        require(columns.all { it in ALLOWED_COLUMNS }) {
            "Only $ALLOWED_COLUMNS are available, requested: $columns"
        }

        val cursor = MatrixCursor(columns.toTypedArray())
        val titles = requireContext().appContainer.database.noteDao().titlesBlocking()
        for (note in titles) {
            cursor.addRow(
                columns.map { column ->
                    when (column) {
                        BaseColumns._ID -> note.id
                        else -> note.title
                    }
                },
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CODE_NOTES -> TYPE_NOTES
        else -> null
    }

    // Только чтение.
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("TitlesProvider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("TitlesProvider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("TitlesProvider is read-only")

    companion object {
        const val TAG = "TitlesProvider"
        const val AUTHORITY = "edu.androidsec.notes.provider"
        const val PERMISSION = "edu.androidsec.notes.permission.READ_NOTES"
        const val PATH_NOTES = "notes"
        const val COLUMN_TITLE = "title"

        private const val CODE_NOTES = 1
        private const val TYPE_NOTES = "vnd.android.cursor.dir/vnd.edu.androidsec.notes.title"
        private val ALLOWED_COLUMNS = listOf(BaseColumns._ID, COLUMN_TITLE)
    }
}
