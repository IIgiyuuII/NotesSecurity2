package edu.androidsec.notes.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer
import edu.androidsec.notes.data.db.SearchQuery
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.SearchSort
import edu.androidsec.notes.ui.list.NoteRow

/** Строку поиска длиннее лимита поле не принимает (правка откатывается целиком). */
private val MaxQueryLength = object : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (length > SearchQuery.MAX_LENGTH) revertAllChanges()
    }
}

@StringRes
private fun SearchSort.labelRes(): Int = when (this) {
    SearchSort.TITLE -> R.string.search_sort_title
    SearchSort.UPDATED_NEWEST -> R.string.search_sort_newest
    SearchSort.UPDATED_OLDEST -> R.string.search_sort_oldest
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel: SearchViewModel = viewModel { SearchViewModel(container.notesRepository) }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    SearchContent(
        query = viewModel.query,
        sort = sort,
        results = results,
        onSortSelected = viewModel::onSortSelected,
        onBack = onBack,
        onOpenNote = onOpenNote,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(
    query: TextFieldState,
    sort: SearchSort,
    results: List<Note>?,
    onSortSelected: (SearchSort) -> Unit,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.note_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                state = query,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = MaxQueryLength,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                for (option in SearchSort.entries) {
                    FilterChip(
                        selected = option == sort,
                        onClick = { onSortSelected(option) },
                        label = { Text(stringResource(option.labelRes())) },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    results == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    results.isEmpty() -> Text(
                        text = stringResource(R.string.search_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { it.id }) { note ->
                            NoteRow(note = note, onClick = { onOpenNote(note.id) })
                        }
                    }
                }
            }
        }
    }
}
