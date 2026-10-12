package com.tealium.prism.mobile.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.mobile.TealiumHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisitorFragmentViewModel : ViewModel() {

    private val _visitorIdFlow = MutableSharedFlow<String>()
    val visitorIdFlow: Flow<String> = _visitorIdFlow.asSharedFlow()

    private val _latestVisitorId = MutableStateFlow("Visitor ID: waiting")

    /**
     * The latest value of the visitor id stream, or a short failure description.
     */
    val latestVisitorId: StateFlow<String> = _latestVisitorId.asStateFlow()

    private val visitorIdSubscription: Disposable? =
        TealiumHelper.shared?.visitorId?.subscribe { result ->
            val visitorId = result.getOrNull()
            _latestVisitorId.value = if (visitorId != null) {
                "Visitor ID: $visitorId"
            } else {
                "Visitor ID not available: ${result.exceptionOrNull()?.javaClass?.simpleName}"
            }
        }

    fun currentVisitorId() {
        TealiumHelper.shared?.visitorId?.current()
            ?.subscribe(::handleNewVisitorId)
    }

    fun regenerateVisitorId() {
        TealiumHelper.shared?.visitorId?.regenerate()
            ?.subscribe(::handleNewVisitorId)
    }

    fun eraseLocalHistory() {
        TealiumHelper.shared?.visitorId?.eraseLocalHistory()
            ?.subscribe(::handleNewVisitorId)
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

    override fun onCleared() {
        visitorIdSubscription?.dispose()
        super.onCleared()
    }
}
