package com.thirutricks.tllplayer.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.thirutricks.tllplayer.SP

class TVGroupModel : ViewModel() {
    private val _tvGroupModel = MutableLiveData<List<TVListModel>>()
    val tvGroupModel: LiveData<List<TVListModel>>
        get() = _tvGroupModel

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    private val _change = MutableLiveData<Boolean>()
    val change: LiveData<Boolean>
        get() = _change

    fun setPosition(position: Int) {
        _position.value = position
    }

    fun setChange() {
        _change.value = true
    }

    fun setTVListModelList(tvListModelList: List<TVListModel>) {
        _tvGroupModel.value = tvListModelList
    }

    fun addTVListModel(tvListModel: TVListModel) {
        if (_tvGroupModel.value == null) {
            _tvGroupModel.value = mutableListOf(tvListModel)
            return
        }

        val newList = _tvGroupModel.value!!.toMutableList()
        newList.add(tvListModel)
        _tvGroupModel.value = newList
    }

    fun clear() {
        _tvGroupModel.value = mutableListOf(getTVListModel(0)!!, getTVListModel(1)!!, getTVListModel(2)!!)
        setPosition(0)
        getTVListModel(1)?.clear() // Clear Favourites
        getTVListModel(2)?.clear() // Clear All channels
    }

    fun getTVListModel(): TVListModel? {
        return getTVListModel(position.value as Int)
    }

    fun getTVListModel(idx: Int): TVListModel? {
        if (idx >= size()) {
            return null
        }
        return _tvGroupModel.value?.get(idx)
    }

    init {
        _position.value = SP.positionGroup
    }

    fun size(): Int {
        if (_tvGroupModel.value == null) {
            return 0
        }

        return _tvGroupModel.value!!.size
    }

    fun swap(idx1: Int, idx2: Int) {
        val list = _tvGroupModel.value?.toMutableList() ?: return
        if (idx1 < list.size && idx2 < list.size) {
            val temp = list[idx1]
            list[idx1] = list[idx2]
            list[idx2] = temp
            _tvGroupModel.value = list
        }
    }
}