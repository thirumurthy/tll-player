package com.thirutricks.tllplayer.models

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonSyntaxException
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.SP
import com.thirutricks.tllplayer.showToast
import com.thirutricks.tllplayer.SecureHttpClient
import com.thirutricks.tllplayer.OrderPreferenceManager
import okhttp3.Request
import io.github.lizongying.Gua
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import com.thirutricks.tllplayer.infrastructure.PlaylistFetchers
import com.thirutricks.tllplayer.infrastructure.LauncherChannelHelper

object TVList {
    private const val TAG = "TVList"
    const val FILE_NAME = "channels.txt"
    const val DEFAULT_CONFIG_URL = "https://tllapp.dpdns.org/tvnexa/v1/admin/channel-pllayer"
    private lateinit var appDirectory: File
    private lateinit var serverUrl: String
    private lateinit var list: List<TV>
    private lateinit var appContext: Context
    var listModel: List<TVModel> = listOf()
    val groupModel = TVGroupModel()

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int>
        get() = _importProgress

    fun init(context: Context) {
        appContext = context.applicationContext
        _position.value = 0
        _importProgress.value = 0

        groupModel.addTVListModel(TVListModel("My Collection", 0))
        groupModel.addTVListModel(TVListModel("Favourites", 1))
        groupModel.addTVListModel(TVListModel("All channels", 2))

        appDirectory = context.filesDir
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(appDirectory, FILE_NAME)
            val str = if (file.exists()) {
                Log.i(TAG, "read $file")
                file.readText()
            } else {
                Log.i(TAG, "read resource")
                context.resources.openRawResource(R.raw.channels).bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }

            try {
                str2List(str)
            } catch (e: Exception) {
                Log.e(TAG, "error $e")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to read the channel, please set it in the menu", Toast.LENGTH_LONG).show()
                }
            }

            if (SP.configAutoLoad && SP.networkConfigs.isNotEmpty()) {
                updateAll()
            }
        }
    }

    // Old unsafe client was moved to PlaylistFetchers
    
    fun updateAll() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                if (size() == 0) {
                     _importProgress.value = 5
                } else {
                     _importProgress.value = 0
                }
            }
            try {
                Log.i(TAG, "Starting concurrent channel imports")
                val configs = SP.networkConfigs
                if (configs.isEmpty()) {
                    withContext(Dispatchers.Main) { 
                        list = emptyList()
                        refreshModels()
                        _importProgress.value = 0 
                    }
                    val file = File(appDirectory, FILE_NAME)
                    if (file.exists()) file.delete()
                    return@launch
                }

                val deferreds = configs.filter { it.isEnabled }.map { config ->
                    async { PlaylistFetchers.fetch(config) }
                }

                val results = deferreds.awaitAll()
                val allChannels = results.flatten()

                withContext(Dispatchers.Main) {
                    _importProgress.value = 60
                }

                val file = File(appDirectory, FILE_NAME)
                if (!file.exists()) {
                    file.createNewFile()
                }

                withContext(Dispatchers.Main) {
                    try {
                         list = allChannels
                         if (allChannels.isNotEmpty()) {
                            val str = com.google.gson.Gson().toJson(list)
                            withContext(Dispatchers.IO) {
                                file.writeText(str)
                            }
                            refreshModels()
                            
                            "Channels imported successfully".showToast()
                            checkChannelsInBackground()

                            _importProgress.value = 100
                        } else {
                            refreshModels()
                            withContext(Dispatchers.IO) {
                                if (file.exists()) file.delete()
                            }
                            "Channel list cleared".showToast()
                            _importProgress.value = 0
                        }
                    } catch (e: Exception) {
                         Log.e(TAG, "Parsing error", e)
                         "Channel import error: ${e.message}".showToast()
                         _importProgress.value = 0
                    }
                }
            } catch (e: Exception) {
                Log.e("", "request error $e")
                 withContext(Dispatchers.Main) {
                    "Channel request error: ${e.message}".showToast()
                 }
            }
        }
    }

    fun update(serverUrl: String) {
        // Fallback for single updates if needed
        this.serverUrl = serverUrl
        updateAll()
    }

    fun parseUri(uri: Uri) {
        if (uri.scheme == "file") {
            val file = uri.toFile()
            Log.i(TAG, "file $file")
            val str = if (file.exists()) {
                Log.i(TAG, "read $file")
                file.readText()
            } else {
                "File does not exist".showToast(Toast.LENGTH_LONG)
                return
            }

            try {
                CoroutineScope(Dispatchers.IO).launch {
                    val success = str2List(str)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            "Channel imported successfully".showToast(Toast.LENGTH_LONG)
                            checkChannelsInBackground()
                        } else {
                            "Channel import failed".showToast(Toast.LENGTH_LONG)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("", "error $e")
                file.deleteOnExit()
                "Failed to read channel".showToast(Toast.LENGTH_LONG)
            }
        } else {
            update(uri.toString())
        }
    }

    suspend fun str2List(str: String): Boolean = withContext(Dispatchers.Default) {
        var string = str.trim()
        val g = Gua()
        if (g.verify(string)) {
            Log.i(TAG, "Content verified with Gua")
            string = g.decode(string)
        } else {
             Log.i(TAG, "Content verification failed or not encrypted")
        }
        
        if (string.isBlank()) {
            Log.e(TAG, "Decrypted string is empty")
            return@withContext false
        }
        
        Log.i(TAG, "Decrypted content preview: ${string.take(100)}")

        // Try to find the start of the JSON array
        val startIndex = string.indexOf('[')
        if (startIndex != -1) {
             string = string.substring(startIndex)
             try {
                val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                // Use lenient Gson to handle malformed JSON
                list = com.google.gson.GsonBuilder().setLenient().create().fromJson(string, type)
                Log.i(TAG, "Import Channel ${list.size}")
            } catch (e: Exception) {
                Log.e(TAG, "parse error $string")
                Log.i(TAG, e.message, e)
                return@withContext false
            }
        } else {
            Log.e(TAG, "No JSON array start found")
            return@withContext false
        }

        refreshModels()
        return@withContext true
    }


    suspend fun refreshModels() = withContext(Dispatchers.Default) {
        if (!::list.isInitialized || list.isEmpty()) {
            Log.w(TAG, "Cannot refresh models: list empty, clearing UI models")
            withContext(Dispatchers.Main) {
                val newGroupModel = TVGroupModel()
                newGroupModel.addTVListModel(TVListModel("My Collection", 0))
                newGroupModel.addTVListModel(TVListModel("Favourites", 1))
                newGroupModel.addTVListModel(TVListModel("All channels", 2))
                groupModel.setTVListModelList(newGroupModel.tvGroupModel.value ?: emptyList())
                listModel = emptyList()
                groupModel.setChange()
                LauncherChannelHelper.updateFavoritesChannel(appContext, this@TVList)
            }
            return@withContext
        }

        // Preparation Phase (Background) - Work with TV objects, NOT TVModel
        val map: MutableMap<String, MutableList<TV>> = mutableMapOf()
        for (v in list) {
            if (v.group !in map) {
                map[v.group] = mutableListOf()
            }
            map[v.group]?.add(v)
        }

        // Apply saved category order
        val categoryOrder = OrderPreferenceManager.getCategoryOrder()
        val categoryRenames = OrderPreferenceManager.getCategoryRenames()
        
        val sortedCategories = if (categoryOrder != null && categoryOrder.isNotEmpty()) {
            val orderedCategories = mutableListOf<String>()
            val unorderedCategories = map.keys.filter { it !in categoryOrder }.toMutableList()
            for (catName in categoryOrder) {
                if (catName in map) {
                    orderedCategories.add(catName)
                }
            }
            orderedCategories.addAll(unorderedCategories)
            orderedCategories
        } else {
            map.keys.toList()
        }

        // Prepare raw data structures for Main thread update
        // Triple<CategoryName, GroupIndex, List<TV>>
        val preparedGroups = mutableListOf<Triple<String, Int, List<TV>>>()
        
        var groupIndex = 3
        // We will assign IDs and build TVModels in the main thread to be safe, 
        // OR we can assign IDs here if 'id' in TV is just an Int and not LiveData.
        // TV.id is Int. So checks are fine.
        // But TVModel creation MUST be on Main.
        
        for (categoryName in sortedCategories) {
            val originalCategoryName = categoryName
            val displayCategoryName = categoryRenames[originalCategoryName] ?: originalCategoryName
            val channels = map[originalCategoryName] ?: continue
            
            // Apply saved channel order
            val channelOrder = OrderPreferenceManager.getChannelOrder(originalCategoryName)
            val channelRenames = OrderPreferenceManager.getChannelRenames()
            
            val sortedChannels = if (channelOrder != null && channelOrder.isNotEmpty()) {
                val urlToModel = channels.associateBy { it.uris.firstOrNull() ?: "" }
                val orderedChannels = mutableListOf<TV>()
                val unorderedChannels = channels.filter { 
                    it.uris.firstOrNull()?.let { url -> url !in channelOrder } ?: true 
                }.toMutableList()
                
                for (url in channelOrder) {
                    urlToModel[url]?.let { orderedChannels.add(it) }
                }
                orderedChannels.addAll(unorderedChannels)
                orderedChannels
            } else {
                channels
            }
            
            // Renaming can happen here safely on TV objects
            for (tv in sortedChannels) {
                 val channelUrl = tv.uris.firstOrNull() ?: ""
                 val renamedTitle = channelRenames[channelUrl]
                 if (renamedTitle != null) {
                     tv.title = renamedTitle
                 }
            }
            
            preparedGroups.add(Triple(displayCategoryName, groupIndex, sortedChannels))
            groupIndex++
        }

        // Update Phase (Main Thread) - Build new model completely before replacing
        withContext(Dispatchers.Main) {
            // Build the new group model completely before replacing the old one
            val newGroupModel = TVGroupModel()
            
            // Add the fixed categories first
            newGroupModel.addTVListModel(TVListModel("My Collection", 0))
            newGroupModel.addTVListModel(TVListModel("Favourites", 1))
            newGroupModel.addTVListModel(TVListModel("All channels", 2))
            
            val listModelNew: MutableList<TVModel> = mutableListOf()
            var id = 0
            
            for ((name, idx, channels) in preparedGroups) {
                // TVListModel init calls _position.value which requires Main Thread
                val tvListModel = TVListModel(name, idx)
                val groupChannels = mutableListOf<TVModel>()

                for ((listIndex, tv) in channels.withIndex()) {
                     tv.id = id
                     // Instantiate TVModel here (Main Thread)
                     val tvModel = TVModel(tv)
                     tvModel.groupIndex = idx
                     tvModel.listIndex = listIndex
                     
                     groupChannels.add(tvModel)
                     listModelNew.add(tvModel)
                     id++
                }

                tvListModel.setTVListModel(groupChannels)
                newGroupModel.addTVListModel(tvListModel)
            }

            // Populate Favourites category (index 1)
            val favouriteChannels = mutableListOf<TVModel>()
            for (tvModel in listModelNew) {
                if (SP.getLike(tvModel.tv.id)) {
                    favouriteChannels.add(tvModel)
                }
            }
            newGroupModel.getTVListModel(1)?.setTVListModel(favouriteChannels)

            // All channels (now index 2)
            newGroupModel.getTVListModel(2)?.setTVListModel(listModelNew)

            // Now atomically replace the old model with the new one
            groupModel.setTVListModelList(newGroupModel.tvGroupModel.value ?: emptyList())
            listModel = listModelNew

            Log.i(TAG, "groupModel ${groupModel.size()}")
            
            groupModel.setChange()
            
            LauncherChannelHelper.updateFavoritesChannel(appContext, this@TVList)
        }
    }

    private fun checkChannelsInBackground() {
        // TEMPORARILY DISABLED: Automatic channel removal to prevent working channels from being removed
        return
        
        if (!SP.channelCheck) return

        CoroutineScope(Dispatchers.IO).launch {
            if (!::list.isInitialized || list.isEmpty()) return@launch

            val initialSize = list.size
            Log.i(TAG, "Starting background channel check. Total: $initialSize")

            val validList = mutableListOf<TV>()
            var removedCount = 0

            val currentList = list.toList()

            for (tv in currentList) {
                var isAlive = false
                if (tv.uris.isEmpty()) {
                    isAlive = false 
                } else {
                    for (uri in tv.uris) {
                        if (checkLink(uri)) {
                            isAlive = true
                            break 
                        }
                    }
                }

                if (isAlive) {
                    validList.add(tv)
                } else {
                    removedCount++
                    Log.i(TAG, "Removing dead channel: ${tv.name}")
                }
            }

            if (removedCount > 0) {
                list = validList
                withContext(Dispatchers.Main) {
                    refreshModels()
                    "$removedCount not working channels removed".showToast(Toast.LENGTH_LONG)
                }
            } else {
                Log.i(TAG, "No dead channels found")
            }
        }
    }

    private fun checkLink(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head() // Try HEAD first
                .build()
            
            val client = SecureHttpClient.client
            var response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.close()
                return true
            }
            response.close()

            // If HEAD fails (e.g. 405), try GET
             val getRequest = Request.Builder()
                .url(url)
                .get()
                .build()
            response = client.newCall(getRequest).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            // Log.d(TAG, "Link check failed for $url: ${e.message}")
            false
        }
    }

    fun getTVModel(): TVModel? {
        return getTVModel(position.value!!)
    }

    fun getTVModel(idx: Int): TVModel? {
        if (idx >= size()) {
            return null
        }
        return listModel[idx]
    }

    fun setPosition(position: Int, updateGroup: Boolean = true): Boolean {
        Log.i(TAG, "setPosition $position/${size()}")
        if (position < 0 || position >= size()) {
            return false
        }

        if (_position.value != position) {
            _position.value = position
        }

        val tvModel = getTVModel(position) ?: return false

        if (updateGroup) {
            groupModel.setPosition(tvModel.groupIndex)
            SP.positionGroup = tvModel.groupIndex
        }
        
        SP.position = position
        return true
    }

    fun size(): Int {
        return listModel.size
    }

    fun refreshFavourites() {
        CoroutineScope(Dispatchers.Main).launch {
            val favouriteChannels = mutableListOf<TVModel>()
            for (tvModel in listModel) {
                if (SP.getLike(tvModel.tv.id)) {
                    favouriteChannels.add(tvModel)
                }
            }
            groupModel.getTVListModel(1)?.setTVListModel(favouriteChannels)
            groupModel.setChange()
            
            LauncherChannelHelper.updateFavoritesChannel(appContext, this@TVList)
        }
    }
    
    /**
     * Reassign channel IDs after moves to maintain correct sequential numbering
     */
    fun reassignChannelIds() {
        CoroutineScope(Dispatchers.Main).launch {
            var id = 0
            for (tvModel in listModel) {
                tvModel.tv.id = id
                id++
            }
            
            // Update the current position if it's set
            val currentPosition = position.value
            if (currentPosition != null && currentPosition < listModel.size) {
                SP.position = currentPosition
            }
            
            Log.i(TAG, "Channel IDs reassigned. Total channels: ${listModel.size}")
        }
    }
    
    /**
     * Rebuild the global channel list based on current category order
     * This is needed when categories are moved to ensure correct channel numbering
     */
    fun rebuildChannelListFromCategories() {
        CoroutineScope(Dispatchers.Main).launch {
            val newListModel = mutableListOf<TVModel>()
            var id = 0
            
            // Skip the first 3 categories (My Collection, Favourites, All channels)
            // and rebuild from the actual content categories
            for (i in 3 until groupModel.size()) {
                val tvListModel = groupModel.getTVListModel(i)
                if (tvListModel != null) {
                    val channels = tvListModel.tvListModel.value ?: emptyList()
                    for ((listIndex, tvModel) in channels.withIndex()) {
                        // Update the channel ID and indices
                        tvModel.tv.id = id
                        tvModel.groupIndex = i
                        tvModel.listIndex = listIndex
                        
                        newListModel.add(tvModel)
                        id++
                    }
                }
            }
            
            // Update the global list model
            listModel = newListModel
            
            // Refresh the "All channels" category (index 2) with the new order
            groupModel.getTVListModel(2)?.setTVListModel(newListModel)
            
            // Refresh favourites to maintain consistency
            refreshFavourites()
            
            // Update the current position if it's set
            val currentPosition = position.value
            if (currentPosition != null && currentPosition < listModel.size) {
                SP.position = currentPosition
            }
            
            Log.i(TAG, "Channel list rebuilt from categories. Total channels: ${listModel.size}")
        }
    }

    fun removeChannel(tv: TV) {
        if (!::list.isInitialized) return
        
        val mutableList = list.toMutableList()
        val removed = mutableList.removeIf { it === tv || (it.group == tv.group && it.title == tv.title && it.uris == tv.uris) }
        
        if (removed) {
            list = mutableList
            CoroutineScope(Dispatchers.Main).launch {
                refreshModels()
            }
        }
    }
}