@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.thirutricks.tllplayer.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.features.home.HeroKind
import com.thirutricks.tllplayer.features.home.HomeConfig
import com.thirutricks.tllplayer.features.home.HomeLiveRowMode
import com.thirutricks.tllplayer.features.home.HomeRow
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository

class HomeSettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
    val config: StateFlow<HomeConfig> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(HomeConfig()) else settings.homeConfig(pid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeConfig())

    fun setRowHidden(row: HomeRow, hidden: Boolean) {
        updateConfig { config -> config.copy(hidden = if (hidden) config.hidden + row else config.hidden - row) }
    }

    fun move(row: HomeRow, up: Boolean) {
        updateConfig { config ->
            val rows = config.settingsRows.toMutableList()
            val from = rows.indexOf(row)
            if (from < 0) {
                config
            } else {
                val to = if (up) from - 1 else from + 1
                if (to !in rows.indices) {
                    config
                } else {
                    rows.add(to, rows.removeAt(from))
                    config.copy(order = rows + config.order.filterNot { it.implemented })
                }
            }
        }
    }

    fun moveToEdge(row: HomeRow, top: Boolean) {
        updateConfig { config ->
            val rows = config.settingsRows.toMutableList()
            val from = rows.indexOf(row)
            if (from < 0) {
                config
            } else {
                val to = if (top) 0 else rows.lastIndex
                if (to == from) {
                    config
                } else {
                    rows.add(to, rows.removeAt(from))
                    config.copy(order = rows + config.order.filterNot { it.implemented })
                }
            }
        }
    }

    fun setHeroInclude(kind: HeroKind, included: Boolean) {
        updateConfig { config ->
            when (kind) {
                HeroKind.LIVE -> config.copy(heroIncludeLive = included)
                HeroKind.MOVIES -> config.copy(heroIncludeMovies = included)
                HeroKind.SERIES -> config.copy(heroIncludeSeries = included)
            }
        }
    }

    fun setLiveRowMode(row: HomeRow, mode: HomeLiveRowMode) {
        updateConfig { config ->
            when (row) {
                HomeRow.RECENT_CHANNELS -> config.copy(recentLiveMode = mode)
                HomeRow.FAVORITE_CHANNELS -> config.copy(favoriteLiveMode = mode)
                else -> config
            }
        }
    }

    private fun updateConfig(transform: (HomeConfig) -> HomeConfig) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            if (pid < 0) return@launch
            settings.updateHomeConfig(pid) { current -> transform(current) }
        }
    }
}
