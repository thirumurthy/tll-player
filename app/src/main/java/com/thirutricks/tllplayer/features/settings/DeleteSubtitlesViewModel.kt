package com.thirutricks.tllplayer.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.core.database.dao.LinkedSubtitle
import com.thirutricks.tllplayer.core.subtitles.SubtitleController

/** Backs the "Delete subtitles" screen (subtitle plan §11): downloaded subs per Movies/Series, with delete. */
class DeleteSubtitlesViewModel(
    private val controller: SubtitleController,
) : ViewModel() {

    enum class Section(val mediaType: String, val label: String) {
        MOVIES("MOVIE", "Movies"), SERIES("SERIES", "Series")
    }

    private val _section = MutableStateFlow(Section.MOVIES)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _items = MutableStateFlow<List<LinkedSubtitle>>(emptyList())
    val items: StateFlow<List<LinkedSubtitle>> = _items.asStateFlow()

    /** Counts per section for the toggle labels + empty states. */
    private val _movieCount = MutableStateFlow(0)
    val movieCount: StateFlow<Int> = _movieCount.asStateFlow()
    private val _seriesCount = MutableStateFlow(0)
    val seriesCount: StateFlow<Int> = _seriesCount.asStateFlow()

    init { refresh() }

    fun selectSection(section: Section) {
        _section.value = section
        reloadItems()
    }

    fun deleteOne(item: LinkedSubtitle) {
        viewModelScope.launch {
            controller.deleteCached(item.cacheId)
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch { controller.deleteAllDownloads(); refresh() }
    }

    fun deleteAllMovies() {
        viewModelScope.launch { controller.deleteAllForType(Section.MOVIES.mediaType); refresh() }
    }

    fun deleteAllSeries() {
        viewModelScope.launch { controller.deleteAllForType(Section.SERIES.mediaType); refresh() }
    }

    private fun refresh() {
        viewModelScope.launch {
            _movieCount.value = controller.downloadsForType(Section.MOVIES.mediaType).size
            _seriesCount.value = controller.downloadsForType(Section.SERIES.mediaType).size
            reloadItems()
        }
    }

    private fun reloadItems() {
        viewModelScope.launch { _items.value = controller.downloadsForType(_section.value.mediaType) }
    }
}
