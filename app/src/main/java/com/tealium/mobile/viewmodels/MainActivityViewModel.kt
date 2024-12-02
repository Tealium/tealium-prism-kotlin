package com.tealium.mobile.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tealium.mobile.TealiumHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainActivityViewModel : ViewModel() {

    private var _notifications = MutableSharedFlow<String>()
    val notifications: Flow<String>
        get() = _notifications.asSharedFlow()

    val isEnabled: Boolean
        get() = TealiumHelper.isEnabled

    fun initialize(application: Application) {
        TealiumHelper.init(application) {
            viewModelScope.launch {
                val error = it.exceptionOrNull()
                if (error != null) {
                    _notifications.emit("Error initializing Tealium: ${error.message}")
                } else {
                    _notifications.emit("Tealium initialized successfully.")
                }
            }
        }
    }

    fun shutdown() {
        TealiumHelper.shutdown()
        viewModelScope.launch {
            _notifications.emit("Tealium shutdown.")
        }
    }

    fun track(eventName: String) {
        TealiumHelper.track(eventName)
    }
}