package com.thirutricks.tllplayer.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.core.subtitles.OpenSubtitlesAccountManager
import com.thirutricks.tllplayer.core.subtitles.OpenSubtitlesAuthStore
import com.thirutricks.tllplayer.core.subtitles.OpenSubtitlesClient
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository

/** Backs the OpenSubtitles account screen (subtitle plan §5.3): active profile's session + sign-in/out. */
class OpenSubtitlesViewModel(
    private val settings: SettingsRepository,
    private val accounts: OpenSubtitlesAccountManager,
) : ViewModel() {

    sealed interface UiState {
        data object SignedOut : UiState
        data object Busy : UiState
        data class SignedIn(val session: OpenSubtitlesAuthStore.Session) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.SignedOut)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** One-shot error text for the §14 dialogs; cleared by [dismissError]. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Show the stored session immediately, then refresh the allowance from the provider.
        viewModelScope.launch {
            val pid = activeProfile() ?: return@launch
            val stored = accounts.session(pid)
            _state.value = stored?.let { UiState.SignedIn(it) } ?: UiState.SignedOut
            if (stored != null) {
                runCatching { accounts.refreshUserInfo(pid) }
                    .onSuccess { updated ->
                        _state.value = updated?.let { UiState.SignedIn(it) } ?: UiState.SignedOut
                    }
                    // Network failure: keep showing the stored snapshot — but say why in the log,
                    // since a silent failure here just looks like a missing Downloads row.
                    .onFailure { android.util.Log.w("OpenSubtitles", "user-info refresh failed: ${it.message}") }
            }
        }
    }

    fun signIn(username: String, password: String, staySignedIn: Boolean) {
        if (username.isBlank() || password.isEmpty()) {
            _error.value = "Enter your OpenSubtitles username and password."
            return
        }
        viewModelScope.launch {
            val pid = activeProfile() ?: return@launch
            _state.value = UiState.Busy
            runCatching { accounts.signIn(pid, username, password, staySignedIn) }
                .onSuccess { _state.value = UiState.SignedIn(it) }
                .onFailure { e ->
                    _state.value = UiState.SignedOut
                    _error.value = if (e is OpenSubtitlesClient.ApiException && e.code == 401) {
                        "OpenSubtitles couldn't sign in with those account details."
                    } else {
                        "Couldn't reach OpenSubtitles. Check your internet connection and try again."
                    }
                }
        }
    }

    /** Manual "Refresh" on the account screen — re-pulls the allowance from /infos/user. */
    fun refresh() {
        viewModelScope.launch {
            val pid = activeProfile() ?: return@launch
            if (state.value !is UiState.SignedIn) return@launch
            runCatching { accounts.refreshUserInfo(pid) }
                .onSuccess { updated ->
                    _state.value = updated?.let { UiState.SignedIn(it) } ?: UiState.SignedOut
                }
                .onFailure {
                    android.util.Log.w("OpenSubtitles", "manual refresh failed: ${it.message}")
                    _error.value = "Couldn't refresh from OpenSubtitles. Check your internet connection."
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val pid = activeProfile() ?: return@launch
            _state.value = UiState.Busy
            runCatching { accounts.signOut(pid) } // local erase happens even if the server call fails
            _state.value = UiState.SignedOut
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private suspend fun activeProfile(): Long? = settings.activeProfileId.first().takeIf { it >= 0 }
}
