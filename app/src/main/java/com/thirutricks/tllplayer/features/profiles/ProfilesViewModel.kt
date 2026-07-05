package com.thirutricks.tllplayer.features.profiles

import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.ProfileEntity
import com.thirutricks.tllplayer.core.database.entity.ProfileSourceCrossRef
import com.thirutricks.tllplayer.core.launcher.LauncherIntegrationRepository
import com.thirutricks.tllplayer.core.util.Pin
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository

/**
 * Phase 6.5 — profile creation/switching and the launch gate's data. Shared by the "Who's watching?"
 * gate and the Settings → Profiles management screen.
 */
class ProfilesViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        /** The reserved name of the always-present default profile. Identity is by NAME (not id), so
         *  protection survives even if the seeded id ever changes (e.g. a migration re-seeds). */
        const val DEFAULT_PROFILE_NAME = "TLL"

        /** True if this profile is the protected default — non-deletable and name-locked to "TLL". */
        fun isDefaultProfile(profile: ProfileEntity): Boolean = profile.name == DEFAULT_PROFILE_NAME
    }

    /** Make [profile] active (routes the app into the shell) once the preference write commits. */
    fun switchTo(profile: ProfileEntity, onSwitched: () -> Unit = {}) {
        viewModelScope.launch {
            settings.setActiveProfile(profile.id)
            onSwitched()
        }
    }

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean = Pin.verify(pin, profile.pinHash)

    /**
     * Create a new profile. New profiles inherit the existing sources (single-account, multi-viewer)
     * so they immediately have content; favorites/history stay per-profile.
     */
    fun create(name: String, avatarId: Int, isKids: Boolean, pin: String?, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = profileDao.insert(
                ProfileEntity(
                    name = name.ifBlank { "Profile" },
                    avatarColor = 0,
                    avatarId = avatarId,
                    isKids = isKids,
                    pinHash = pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) },
                ),
            )
            // Link every existing source to the new profile.
            sourceDao.observeForProfileOnceLinked(id)
            onCreated(id)
        }
    }

    /** Apply edits from the editor dialog. [pin]: null = keep the existing PIN, "" = remove it.
     *  The default profile's name is locked to "TLL" (avatar/kids/PIN stay editable). */
    fun edit(profile: ProfileEntity, name: String, avatarId: Int, isKids: Boolean, pin: String?) {
        viewModelScope.launch {
            val pinHash = when {
                pin == null -> profile.pinHash
                pin.isEmpty() -> null
                else -> Pin.hash(pin)
            }
            // The default profile keeps its reserved name; every other field is editable.
            val resolvedName = if (isDefaultProfile(profile)) DEFAULT_PROFILE_NAME
                else name.ifBlank { profile.name }
            profileDao.update(profile.copy(name = resolvedName, avatarId = avatarId, isKids = isKids, pinHash = pinHash))
        }
    }

    fun rename(profile: ProfileEntity, name: String) {
        viewModelScope.launch {
            // The default profile can't be renamed away from "TLL".
            if (isDefaultProfile(profile)) return@launch
            profileDao.update(profile.copy(name = name.ifBlank { profile.name }))
        }
    }

    fun setKids(profile: ProfileEntity, isKids: Boolean) {
        viewModelScope.launch { profileDao.update(profile.copy(isKids = isKids)) }
    }

    fun setPin(profile: ProfileEntity, pin: String?) {
        viewModelScope.launch { profileDao.setPin(profile.id, pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) }) }
    }

    fun delete(profile: ProfileEntity) {
        viewModelScope.launch {
            // Never delete the default profile (by name, not id) or the last profile.
            if (isDefaultProfile(profile) || profileDao.count() <= 1) return@launch
            val activeProfileId = settings.activeProfileId.first()
            val remainingProfileId = profileDao.getAllOnce().firstOrNull { it.id != profile.id }?.id
            runCatching { launcherIntegrationRepository.clearProfile(profile.id) }
            profileDao.delete(profile)
            if (activeProfileId == profile.id) {
                settings.setActiveProfile(remainingProfileId ?: -1L)
            }
        }
    }
}

/** Links all currently-known sources to a freshly created profile (helper kept off the entity API). */
private suspend fun SourceDao.observeForProfileOnceLinked(profileId: Long) {
    // All sources currently belong to existing profiles; share them with the new one.
    val allSourceIds = allSourceIds()
    allSourceIds.forEach { link(ProfileSourceCrossRef(profileId = profileId, sourceId = it)) }
}
