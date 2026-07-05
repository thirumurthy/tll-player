package com.thirutricks.tllplayer.core.database.dao

/**
 * Returns the current profile id when it still exists, otherwise falls back to the first profile
 * row in the database.
 *
 * This keeps profile-scoped writes from crashing when the stored active profile id is stale or was
 * deleted before the caller ran.
 */
suspend fun ProfileDao.resolveExistingProfileId(preferredId: Long): Long? {
    if (preferredId < 0) return null
    getById(preferredId)?.let { return it.id }
    return getAllOnce().firstOrNull()?.id
}
