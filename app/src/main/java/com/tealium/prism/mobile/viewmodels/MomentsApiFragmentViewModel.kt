package com.tealium.prism.mobile.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.mobile.TealiumHelper
import com.tealium.prism.momentsapi.EngineResponse
import com.tealium.prism.momentsapi.momentsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class MomentsApiFragmentViewModel : ViewModel() {

    private val _engineResponseFlow = MutableSharedFlow<EngineResponse>()
    val engineResponseFlow: Flow<EngineResponse> = _engineResponseFlow.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: Flow<String> = _errorFlow.asSharedFlow()

    private val _savedEngineIdFlow = MutableStateFlow<String>("")
    val savedEngineIdFlow: StateFlow<String> = _savedEngineIdFlow.asStateFlow()

    private fun emitError(message: String) {
        viewModelScope.launch {
            _errorFlow.emit(message)
        }
    }

    fun fetchEngineResponse(engineID: String) {
        if (engineID.isBlank()) {
            emitError("Engine ID cannot be empty")
            return
        }

        val momentsApi = TealiumHelper.shared?.momentsApi
        if (momentsApi == null) {
            emitError("MomentsApi module is not initialized")
            return
        }

        momentsApi.fetchEngineResponse(engineID).subscribe(::handleEngineResponse)
    }

    private fun handleEngineResponse(result: TealiumResult<EngineResponse>) {
        try {
            val response = result.getOrThrow()
            viewModelScope.launch {
                _engineResponseFlow.emit(response)
            }
        } catch (e: TealiumException) {
            val errorMessage = e.message ?: "Unknown error"
            Log.e(
                MomentsApiFragmentViewModel::class.java.simpleName,
                "MomentsApi Error: $errorMessage",
                e
            )
            emitError("Error: $errorMessage")
        }
    }

    fun saveEngineId(context: Context, engineId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sharedPreferences = context.getSharedPreferences("moments_api", Context.MODE_PRIVATE)
            sharedPreferences.edit { putString("engine_id", engineId) }
        }
    }

    fun loadSavedEngineId(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val sharedPreferences = context.getSharedPreferences("moments_api", Context.MODE_PRIVATE)
            val savedEngineId = sharedPreferences.getString("engine_id", "") ?: ""
            withContext(Dispatchers.Main) {
                _savedEngineIdFlow.value = savedEngineId
            }
        }
    }
}

