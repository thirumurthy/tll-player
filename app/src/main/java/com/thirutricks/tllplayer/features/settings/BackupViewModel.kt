package com.thirutricks.tllplayer.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.core.backup.BackupManager
import java.io.File

/** Phase 12 — drives Backup & Restore (selective export/import to a JSON file). */
class BackupViewModel(private val backup: BackupManager) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Working : State

        /** A restore file was picked & inspected: let the user choose which sections to apply. */
        data class ChooseRestore(val file: File, val available: Set<BackupManager.Section>) : State
        data class Done(val message: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun export(folder: File, sections: Set<BackupManager.Section>) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.export(folder, sections).fold(
                onSuccess = { _state.value = State.Done("Saved to $it") },
                onFailure = { _state.value = State.Error(it.message ?: "Export failed") },
            )
        }
    }

    /** Step 1 of restore: inspect the picked file so the section picker can show what's inside. */
    fun inspect(file: File) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.sectionsIn(file).fold(
                onSuccess = { _state.value = State.ChooseRestore(file, it) },
                onFailure = { _state.value = State.Error(it.message ?: "Couldn't read the backup file") },
            )
        }
    }

    /** Step 2 of restore: apply the chosen sections. */
    fun import(file: File, sections: Set<BackupManager.Section>) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.import(file, sections).fold(
                onSuccess = { _state.value = State.Done("Restored $it items. Re-sync your sources to load content.") },
                onFailure = { _state.value = State.Error(it.message ?: "Import failed") },
            )
        }
    }

    fun reset() { _state.value = State.Idle }
}
