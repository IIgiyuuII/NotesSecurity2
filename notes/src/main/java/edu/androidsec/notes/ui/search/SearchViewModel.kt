package edu.androidsec.notes.ui.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.SearchSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Поиск: строка запроса (состояние поля) и выбранная сортировка превращаются в поток
 * результатов из репозитория. При смене запроса или сортировки прежний запрос отменяется
 * ([flatMapLatest]), результаты обновляются и при изменении самой базы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(repository: NotesRepository) : ViewModel() {

    val query = TextFieldState()

    private val _sort = MutableStateFlow(SearchSort.UPDATED_NEWEST)
    val sort: StateFlow<SearchSort> = _sort.asStateFlow()

    /** `null` — результаты ещё загружаются. */
    val results: StateFlow<List<Note>?> = combine(
        snapshotFlow { query.text.toString() },
        _sort,
    ) { text, sort -> text to sort }
        .distinctUntilChanged()
        .flatMapLatest { (text, sort) -> repository.search(text, sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onSortSelected(sort: SearchSort) {
        _sort.value = sort
    }
}
