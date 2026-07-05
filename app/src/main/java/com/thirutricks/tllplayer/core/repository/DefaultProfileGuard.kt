package com.thirutricks.tllplayer.core.repository

import android.util.Log
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.ProfileEntity
import com.thirutricks.tllplayer.core.database.entity.ProfileSourceCrossRef
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.features.profiles.ProfilesViewModel
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Guarantees the app always has its default "TLL" profile and its default TLL source.
 *
 * The profile + source are seeded once on first DB creation (see [DatabaseModule]'s onCreate callback),
 * but a bad migration, a manual DB wipe, or any future schema jump could drop them — leaving the app
 * with no profile and no content. This guard runs at startup and re-creates anything missing, so the app
 * is never left without a default profile.
 */
class DefaultProfileGuard(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
) {
    /** Recreate the default TLL profile/source if either is missing. Safe to call every launch. */
    suspend fun ensureDefaults() {
        val now = System.currentTimeMillis()
        try {
            val profile = profileDao.getByName(ProfilesViewModel.DEFAULT_PROFILE_NAME)
            val profileId = if (profile == null) {
                val newId = profileDao.insert(
                    ProfileEntity(name = ProfilesViewModel.DEFAULT_PROFILE_NAME, avatarColor = 0, avatarId = 0, createdAt = now),
                )
                Log.i(TAG, "Re-seeded missing default profile (id=$newId)")
                ensureDefaultSource(newId, now)
                newId
            } else {
                // Profile exists — make sure it also has the default source linked.
                ensureDefaultSource(profile.id, now)
                profile.id
            }

            val activeId = settings.activeProfileId.first()
            if (activeId < 0L) {
                settings.setActiveProfile(profileId)
                Log.i(TAG, "DefaultProfileGuard: Set active profile to default profile (id=$profileId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure default profile/source", e)
        }
    }

    private suspend fun ensureDefaultSource(profileId: Long, now: Long) {
        val sourceIds = sourceDao.sourceIdsForProfile(profileId)
        val all = sourceDao.getAllOnce()
        val hasDefault = sourceIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
            .any { it.type == SourceType.TLL }
        if (sourceIds.isEmpty() || !hasDefault) {
            val sourceId = sourceDao.insert(
                SourceEntity(name = "TLL Source", type = SourceType.TLL, url = DEFAULT_TLL_URL, createdAt = now),
            )
            sourceDao.link(ProfileSourceCrossRef(profileId = profileId, sourceId = sourceId))
            Log.i(TAG, "Re-seeded missing default TLL source (id=$sourceId) for profile $profileId")
        }
    }

    companion object {
        private const val TAG = "DefaultProfileGuard"
        /** The default TLL endpoint. Mirrors the one seeded in DatabaseModule.onCreate. */
        const val DEFAULT_TLL_URL = "https://tllapp.dpdns.org/tvnexa/v1/admin/channel-pllayer"
    }
}
