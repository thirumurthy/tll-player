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
import io.github.lizongying.Gua
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object TVList {
    private const val TAG = "TVList"
    const val FILE_NAME = "channels.txt"
    private lateinit var appDirectory: File
    private lateinit var serverUrl: String
    private lateinit var list: List<TV>
    var listModel: List<TVModel> = listOf()
    val groupModel = TVGroupModel()

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    fun init(context: Context) {
        _position.value = 0

        groupModel.addTVListModel(TVListModel("My Collection", 0))
        groupModel.addTVListModel(TVListModel("All channels", 1))

        appDirectory = context.filesDir
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
            Log.e("", "error $e")
            file.deleteOnExit()
            Toast.makeText(context, "Failed to read the channel, please set it in the menu", Toast.LENGTH_LONG).show()
        }

        if (SP.configAutoLoad && !SP.config.isNullOrEmpty()) {
            SP.config?.let {
                update(it)
            }
        }
    }

    private fun update() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "request $serverUrl")
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(serverUrl).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val file = File(appDirectory, FILE_NAME)
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    val str = response.body()!!.string()
                    withContext(Dispatchers.Main) {
                        if (str2List(str)) {
                            file.writeText(str)
                            SP.config = serverUrl
                            "Channel imported successfully".showToast()
                        } else {
                            "Channel import error".showToast()
                        }
                    }
                } else {
                    Log.e("", "request status ${response.code()}")
                    "Channel status error".showToast()
                }
            } catch (e: JsonSyntaxException) {
                Log.e("JSON Parse Error", e.toString())
                "Channel format error".showToast()
            } catch (e: NullPointerException) {
                Log.e("Null Pointer Error", e.toString())
                "Unable to read channel".showToast()
            } catch (e: Exception) {
                Log.e("", "request error $e")
                "Channel request error".showToast()
            }
        }
    }

    private fun update(serverUrl: String) {
        this.serverUrl = serverUrl
        update()
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
                if (str2List(str)) {
                    SP.config = uri.toString()
                    "Channel imported successfully".showToast(Toast.LENGTH_LONG)
                } else {
                    "Channel import failed".showToast(Toast.LENGTH_LONG)
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

    fun str2List(str: String): Boolean {
        var string = str
        val g = Gua()
        if (g.verify(str)) {
            string = g.decode(str)
        }
        if (string.isBlank()) {
            return false
        }
        when (string[0]) {
            '[' -> {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                    list = com.google.gson.Gson().fromJson(string, type)
                    Log.i(TAG, "Import Channel ${list.size}")
                } catch (e: Exception) {
                    Log.i(TAG, "parse error $string")
                    Log.i(TAG, e.message, e)
                    return false
                }
            }
        }

        groupModel.clear()

        val map: MutableMap<String, MutableList<TVModel>> = mutableMapOf()
        for (v in list) {
            if (v.group !in map) {
                map[v.group] = mutableListOf()
            }
            map[v.group]?.add(TVModel(v))
        }

        val listModelNew: MutableList<TVModel> = mutableListOf()
        var groupIndex = 2
        var id = 0
        for ((k, v) in map) {
            val tvListModel = TVListModel(k, groupIndex)
            for ((listIndex, v1) in v.withIndex()) {
                v1.tv.id = id
                v1.groupIndex = groupIndex
                v1.listIndex = listIndex
                tvListModel.addTVModel(v1)
                listModelNew.add(v1)
                id++
            }
            groupModel.addTVListModel(tvListModel)
            groupIndex++
        }

        listModel = listModelNew

        // 全部频道
        groupModel.getTVListModel(1)?.setTVListModel(listModel)

        Log.i(TAG, "groupModel ${groupModel.size()}")
        groupModel.setChange()

        return true
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

    fun setPosition(position: Int): Boolean {
        Log.i(TAG, "setPosition $position/${size()}")
        if (position >= size()) {
            return false
        }

        if (_position.value != position) {
            _position.value = position
        }

        val tvModel = getTVModel(position)

        // set a new position or retry when position same
        tvModel!!.setReady()

        groupModel.setPosition(tvModel.groupIndex)

        SP.positionGroup = tvModel.groupIndex
        SP.position = position
        return true
    }

    fun size(): Int {
        return listModel.size
    }
}