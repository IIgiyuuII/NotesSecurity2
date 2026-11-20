package edu.androidsec.notes.photo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Выбор фото без разрешений на медиафайлы (ipc-2).
 *
 * Системный Photo Picker сам показывает галерею и возвращает `content://`-URI с временным
 * правом на чтение только выбранного файла. Приложению не нужны ни `READ_MEDIA_IMAGES`, ни
 * `READ_EXTERNAL_STORAGE`, ни `CAMERA`. Если пользователь ничего не выбрал, URI равен null и
 * [onPicked] не вызывается. На старых устройствах без Photo Picker библиотека сама
 * откатывается на системный `ACTION_OPEN_DOCUMENT`, который тоже работает без разрешений.
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) currentOnPicked(uri)
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
