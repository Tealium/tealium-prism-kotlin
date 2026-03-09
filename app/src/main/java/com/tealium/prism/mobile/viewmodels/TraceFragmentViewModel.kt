package com.tealium.prism.mobile.viewmodels

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tealium.prism.mobile.TealiumHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TraceFragmentViewModel(private val sharedPreferencesProvider: () -> SharedPreferences) : ViewModel() {

    private val sharedPreferences by lazy {
        sharedPreferencesProvider.invoke()
    }

    private val _traceId = MutableStateFlow("")
    val traceId = _traceId.asStateFlow()

    init {
        viewModelScope.launch {
            _traceId.value = retrieveTraceId() ?: ""
        }
    }

    fun onTraceIdUpdated(traceId: String) {
        _traceId.value = traceId
    }

    fun joinTrace() {
        val currentTraceId = traceId.value
        if (currentTraceId.isNotEmpty()) {
            TealiumHelper.joinTrace(currentTraceId)
            saveTraceId()
        }
    }

    fun leaveTrace() {
        TealiumHelper.leaveTrace()
        removeTraceId()
    }

    fun endVisitorSession() {
        TealiumHelper.endVisitorSession()
    }

    private suspend fun retrieveTraceId(): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(PREFS_TRACE_ID_KEY, "")
    }

    private fun saveTraceId() {
        viewModelScope.launch(Dispatchers.IO) {
            sharedPreferences.edit { putString(PREFS_TRACE_ID_KEY, traceId.value) }
        }
    }

    private fun removeTraceId() {
        viewModelScope.launch(Dispatchers.IO) {
            sharedPreferences.edit { remove(PREFS_TRACE_ID_KEY) }
        }
    }

    companion object {
        const val PREFS_TRACE_ID_KEY = "traceId"

        fun provideFactory(sharedPreferencesProvider: () -> SharedPreferences) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TraceFragmentViewModel(sharedPreferencesProvider) as T
                }
            }
    }
}