package com.tealium.mobile.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.misc.TealiumResult
import com.tealium.mobile.TealiumHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class VisitorFragmentViewModel : ViewModel() {

    private val _visitorIdFlow = MutableSharedFlow<String>()
    val visitorIdFlow: Flow<String> = _visitorIdFlow.asSharedFlow()

    fun resetVisitorId() {
        TealiumHelper.shared?.resetVisitorId(::handleNewVisitorId)
    }

    fun clearStoredVisitorIds() {
        TealiumHelper.shared?.clearStoredVisitorIds(::handleNewVisitorId)
    }

    private fun handleNewVisitorId(result: TealiumResult<String>) {
        try {
            val visitorId = result.getOrThrow()
            viewModelScope.launch {
                _visitorIdFlow.emit(visitorId)
            }
        } catch (e: TealiumException) {
            Log.w(
                VisitorFragmentViewModel::class.java.simpleName,
                "Error during Visitor Id update.",
                e
            )
        }
    }
}