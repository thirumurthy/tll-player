package com.thirutricks.tllplayer.infrastructure

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.thirutricks.tllplayer.MainActivity
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.SP
import com.thirutricks.tllplayer.models.TV
import com.thirutricks.tllplayer.models.TVList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LauncherChannelHelper {
    private const val TAG = "LauncherChannelHelper"
    private const val FAVORITES_CHANNEL_ID_KEY = "favorites_channel_id"

    fun updateFavoritesChannel(context: Context, tvList: TVList) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val channelId = getOrCreateFavoritesChannel(context)
                if (channelId != -1L) {
                    syncPrograms(context, channelId, tvList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating favorites channel", e)
            }
        }
    }

    private fun getOrCreateFavoritesChannel(context: Context): Long {
        val sharedPreferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        var channelId = sharedPreferences.getLong(FAVORITES_CHANNEL_ID_KEY, -1L)

        if (channelId != -1L) {
            // Verify channel still exists
            val cursor = context.contentResolver.query(
                TvContractCompat.buildChannelUri(channelId),
                null, null, null, null
            )
            if (cursor == null || !cursor.moveToFirst()) {
                channelId = -1L
            }
            cursor?.close()
        }

        if (channelId == -1L) {
            val channel = Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(context.getString(R.string.favorites))
                .setAppLinkIntentUri(Uri.parse("tllplayer://favorites"))
                .build()

            val uri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
            )

            if (uri != null) {
                channelId = ContentUris.parseId(uri)
                sharedPreferences.edit().putLong(FAVORITES_CHANNEL_ID_KEY, channelId).apply()
                TvContractCompat.requestChannelBrowsable(context, channelId)
                
                // Set logo
                val logoUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
                ChannelLogoUtils.storeChannelLogo(context, channelId, logoUri)
            }
        }

        return channelId
    }

    private fun syncPrograms(context: Context, channelId: Long, tvList: TVList) {
        // Clear existing programs
        context.contentResolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            null, null
        )

        // Get liked channels
        val likedTvList = mutableListOf<TV>()
        // We need to iterate through all groups and channels to find liked ones
        // Assuming tvList.tvGroups contains categories which contain channels
        // But TVList.kt structure might be different. Let's refer to TVList.kt
        
        // After checking TVList.kt, it has _tvList which is LiveData<MutableList<TVModel>>
        // Actually TVList has a private field _tvList and a public getTVModel(idx)
        // Let's use getTVModel and size()
        
        for (i in 0 until tvList.size()) {
            val model = tvList.getTVModel(i)
            if (model != null && SP.getLike(model.tv.id)) {
                likedTvList.add(model.tv)
            }
        }

        likedTvList.forEach { tv ->
            val firstUri = tv.uris.firstOrNull() ?: ""
            // Base64 encode the URL to use it safely in the deep link path
            val encodedUrl = android.util.Base64.encodeToString(firstUri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("tllplayer://play/$encodedUrl")
            }

            val programBuilder = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(tv.title.ifEmpty { tv.name })
                .setDescription(tv.description ?: "")
                .setPosterArtUri(Uri.parse(tv.logo))
                .setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
                .setInternalProviderId(firstUri)
            
            // Add preview video URI for auto-play when focused
            if (firstUri.isNotEmpty()) {
                programBuilder.setPreviewVideoUri(Uri.parse(firstUri))
            }

            context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                programBuilder.build().toContentValues()
            )
        }
    }
}
