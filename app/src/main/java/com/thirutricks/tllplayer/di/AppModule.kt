package com.thirutricks.tllplayer.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import com.thirutricks.tllplayer.features.customize.CustomizeViewModel
import com.thirutricks.tllplayer.features.downloads.DownloadsViewModel
import com.thirutricks.tllplayer.features.epg.EpgViewModel
import com.thirutricks.tllplayer.features.live.LiveViewModel
import com.thirutricks.tllplayer.features.home.HomeViewModel
import com.thirutricks.tllplayer.features.movies.MovieViewModel
import com.thirutricks.tllplayer.features.profiles.ProfilesViewModel
import com.thirutricks.tllplayer.features.search.SearchViewModel
import com.thirutricks.tllplayer.features.series.SeriesViewModel
import com.thirutricks.tllplayer.features.settings.BackupViewModel
import com.thirutricks.tllplayer.features.settings.SettingsViewModel
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.features.setup.SetupViewModel
import com.thirutricks.tllplayer.features.shell.ShellViewModel

/**
 * Root Koin module. Each feature will contribute its own bindings as the app grows;
 * for now this wires settings persistence and the shell view model.
 */
val appModule = module {
    single { SettingsRepository(androidContext()) }
    // Merged (v4.0.0 + PR#31 Home/launcher). Koin resolves each get() by type, so only the count must match
    // each ViewModel's merged constructor.
    viewModel { ShellViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SetupViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { LiveViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MovieViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SeriesViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProfilesViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { DownloadsViewModel(get(), get(), get(), get()) }
    viewModel { EpgViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // settings, sourceDao, categoryDao, customizationStore
    viewModel { CustomizeViewModel(get(), get(), get(), get()) }
    // backupManager
    viewModel { BackupViewModel(get()) }
    // store, epgRepository, sourceRepository, settings, connectivity, epgDao
    viewModel { com.thirutricks.tllplayer.features.settings.EpgSourcesViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
