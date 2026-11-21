package com.illustar0.teishucan.ui

import androidx.lifecycle.ViewModel
import com.illustar0.teishucan.data.CampusRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 应用根部的状态：不属于任何页面的刷新结果。 */
@HiltViewModel
class AppViewModel @Inject constructor(private val refresher: CampusRefresher) : ViewModel() {
    val refreshFailure = refresher.refreshFailure

    fun onRefreshFailureShown(failure: Exception) = refresher.onRefreshFailureShown(failure)
}
