package edu.androidsec.notes.ui.list

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteValidator
import edu.androidsec.notes.ui.navigation.NoteRoute
import kotlinx.coroutines.withTimeoutOrNull

/** Снекбар исчезает чуть раньше окончательного удаления: «Отменить» не должна опаздывать. */
private const val UNDO_MARGIN_MILLIS = 200L

@Composable
fun NotesListScreen(onOpenNote: (Long) -> Unit, onOpenSearch: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel: NotesListViewModel = viewModel {
        NotesListViewModel(container.notesRepository, container.noteDeleter)
    }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val pending by viewModel.pendingDeletion.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.note_deleted)
    val undoText = stringResource(R.string.note_undo)

    // Снекбар строится по состоянию «есть заметка, которую ещё можно вернуть», а не по разовому
    // событию: так он переживает поворот экрана и возврат с экрана заметки. Показываем его
    // ровно на оставшееся до окончательного удаления время.
    LaunchedEffect(pending) {
        val request = pending ?: return@LaunchedEffect
        val remaining = request.purgeAtElapsedMillis - SystemClock.elapsedRealtime() - UNDO_MARGIN_MILLIS
        if (remaining <= 0) return@LaunchedEffect
        val result = withTimeoutOrNull(remaining) {
            snackbarHostState.showSnackbar(
                message = deletedText,
                actionLabel = undoText,
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(request.noteId)
    }

    NotesListContent(
        notes = notes,
        snackbarHostState = snackbarHostState,
        onOpen = onOpenNote,
        onAdd = { onOpenNote(NoteRoute.NEW_NOTE_ID) },
        onDelete = viewModel::delete,
        onSearch = onOpenSearch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListContent(
    notes: List<Note>?,
    snackbarHostState: SnackbarHostState,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onSearch: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.list_search))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.list_add))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                notes == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                notes.isEmpty() -> Text(
                    text = stringResource(R.string.list_empty),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onClick = { onOpen(note.id) },
                            onDelete = { onDelete(note.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка заметки; общая для списка и поиска. Без [onDelete] кнопки удаления нет.
 * У секретной заметки показываются только заголовок и иконка замка: ни текста, ни тегов.
 */
@Composable
internal fun NoteRow(note: Note, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isSecret) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.note_secret_icon),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                        )
                    }
                    Text(
                        text = note.title.ifBlank { stringResource(R.string.note_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (!note.isSecret) {
                    if (note.body.isNotBlank()) {
                        Text(
                            text = note.body,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (note.tags.isNotEmpty()) {
                        Text(
                            text = NoteValidator.formatTags(note.tags),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.list_delete))
                }
            }
        }
    }
}
