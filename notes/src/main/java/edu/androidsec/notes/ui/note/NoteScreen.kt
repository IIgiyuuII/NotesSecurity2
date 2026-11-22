package edu.androidsec.notes.ui.note

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer
import edu.androidsec.notes.domain.NoteError
import edu.androidsec.notes.domain.NoteValidator
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.photo.rememberPhotoPicker
import edu.androidsec.notes.reminder.rememberNotificationPermission
import edu.androidsec.notes.share.ShareNote

@Composable
fun NoteScreen(noteId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel: NoteViewModel = viewModel(key = "note-$noteId") {
        NoteViewModel(noteId, container.notesRepository, container.photoStore, container.reminderScheduler, container.noteDeleter)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Системные диалоги (разрешение, выбор фото) живут в UI; результат уходит во ViewModel.
    val requestNotificationPermission = rememberNotificationPermission(onGranted = viewModel::scheduleReminder)
    val pickPhoto = rememberPhotoPicker(onPicked = viewModel::onPhotoPicked)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NoteEvent.Message -> Toast.makeText(context, event.resId, Toast.LENGTH_SHORT).show()
                NoteEvent.Close -> onBack()
            }
        }
    }

    NoteContent(
        state = state,
        form = viewModel.form,
        photoStore = container.photoStore,
        onBack = onBack,
        onDelete = viewModel::delete,
        onAttachPhoto = pickPhoto,
        onRemovePhoto = viewModel::removePhoto,
        onSave = viewModel::save,
        onRemind = requestNotificationPermission,
        onShare = { ShareNote.shareNoteAsFile(context, viewModel.currentNote()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteContent(
    state: NoteUiState,
    form: NoteForm,
    photoStore: PhotoStore,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onAttachPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onRemind: () -> Unit,
    onShare: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.note_title_new else R.string.note_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.note_back),
                        )
                    }
                },
                actions = {
                    if (!state.notFound) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.note_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notFound -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.note_not_found), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.note_not_found_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.note_back)) }
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        state = form.title,
                        label = { Text(stringResource(R.string.note_field_title)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = form.titleFilter,
                        isError = form.titleError != null,
                        supportingText = {
                            SupportText(
                                error = form.titleError,
                                normal = stringResource(
                                    R.string.field_counter,
                                    symbolCount(form.title.text),
                                    NoteValidator.TITLE_MAX_LENGTH,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        state = form.body,
                        label = { Text(stringResource(R.string.note_field_body)) },
                        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4),
                        inputTransformation = form.bodyFilter,
                        isError = form.bodyError != null,
                        supportingText = {
                            SupportText(
                                error = form.bodyError,
                                normal = stringResource(
                                    R.string.field_counter,
                                    symbolCount(form.body.text),
                                    NoteValidator.BODY_MAX_LENGTH,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        state = form.tags,
                        label = { Text(stringResource(R.string.note_field_tags)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = form.tagsFilter,
                        // Заглавная буква отклоняется белым списком, поэтому клавиатура
                        // не должна сама делать первую букву заглавной или «исправлять» слова.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        isError = form.tagsError != null,
                        supportingText = {
                            SupportText(
                                error = form.tagsError,
                                normal = stringResource(R.string.tags_hint),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.photoPath?.let { NotePhoto(path = it, photoStore = photoStore) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAttachPhoto) { Text(stringResource(R.string.note_attach_photo)) }
                        if (state.photoPath != null) {
                            OutlinedButton(onClick = onRemovePhoto) { Text(stringResource(R.string.note_remove_photo)) }
                        }
                    }

                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_save))
                    }
                    OutlinedButton(onClick = onRemind, enabled = !state.isNew, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_remind))
                    }
                    OutlinedButton(onClick = onShare, enabled = !state.isNew, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.note_share))
                    }
                    if (state.isNew) {
                        Text(stringResource(R.string.note_save_first), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Под полем: причина отказа, если она есть, иначе обычная подсказка (счётчик или правило). */
@Composable
private fun SupportText(error: NoteError?, normal: String) {
    Text(text = if (error != null) stringResource(error.messageRes()) else normal)
}

/** Длина в символах Unicode: так же считает [NoteValidator] (эмодзи — один символ). */
private fun symbolCount(text: CharSequence): Int = Character.codePointCount(text, 0, text.length)

@Composable
private fun NotePhoto(path: String, photoStore: PhotoStore) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = photoStore.loadPreview(path)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = stringResource(R.string.note_photo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
    }
}
