package com.tealium.mobile

import android.app.Application
import android.util.Log
import com.tealium.core.Environment
import com.tealium.core.LogLevel
import com.tealium.core.Modules
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.api.ConsentStatus
import com.tealium.core.api.DataLayer
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Expiry
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.VisitorService
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.listeners.ConsentStatusUpdatedListener
import com.tealium.core.api.listeners.DispatchDroppedListener
import com.tealium.core.api.listeners.DispatchQueuedListener
import com.tealium.core.api.listeners.DispatchReadyListener
import com.tealium.core.api.network.Cancelled
import com.tealium.core.api.network.DoNotRetry
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.IOError
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.Non200Error
import com.tealium.core.api.network.RetryAfterDelay
import com.tealium.core.api.network.RetryPolicy
import com.tealium.core.api.network.Success
import com.tealium.core.api.network.UnexpectedError
import com.tealium.core.api.settings.CoreSettingsBuilder
import com.tealium.core.api.TrackResult
import com.tealium.core.api.data.TealiumValue

object TealiumHelper :
    DispatchReadyListener,
    DispatchQueuedListener,
    DispatchDroppedListener,
    ConsentStatusUpdatedListener {

    private const val INSTANCE_NAME = "main"

    private val shared: Tealium?
        get() = Tealium[INSTANCE_NAME]

    private fun onVisitorIdUpdated(visitorId: String) {
        Log.d("OnMain?", "Executing on ${Thread.currentThread().name}")
        Log.d("Helper", "This Updated VisitorId: $visitorId")
    }

    private fun onDataUpdated(bundle: TealiumBundle) {
        for (entry in bundle) {
            onDataUpdated(entry.key, entry.value)
        }
    }

    private fun onDataUpdated(key: String, value: Any) {
        Log.d("Helper", "DataUpdated $key : $value")
    }

    private fun onDataRemoved(keys: List<String>) {
        Log.d("Helper", "DataRemoved ${keys.joinToString(", ")}")
    }

    fun init(application: Application) {
        val config = TealiumConfig(
            application = application,
            modules = listOf(Modules.VisitorService, Modules.Collect),
            accountName = "tealiummobile",
            profileName = "android",
            environment = Environment.DEV
        ).apply {
            useRemoteSettings = false
//            localSdkSettingsFileName = "tealium-settings.json"

            addModuleSettings(
                CoreSettingsBuilder()
                    .setLogLevel(LogLevel.TRACE)
                    .setBatchSize(8)
            )
        }

        Tealium.create(INSTANCE_NAME, config) { result ->
            val tealium = result.getOrNull() ?: return@create

//            it.events.subscribe(this)
//            it.track("", TealiumDispatchType.Event) {
//                put
//            }
            tealium.dataLayer.onDataUpdated.subscribe(::onDataUpdated)
            tealium.dataLayer.onDataRemoved.subscribe(::onDataRemoved)
            tealium.dataLayer.onDataRemoved.subscribe {
                it.forEach {
                    Log.d("Lambda", "Removed: key: $it")
                }
            }

            tealium.dataLayer.edit {
                it.put("key", TealiumValue.string("value"), Expiry.SESSION)
                it.put("key2", TealiumValue.string("value2"), Expiry.SESSION)
                it.remove("key2")
            }
            tealium.dataLayer.get("key") {
                Log.d("DataLayer", "Retrieved key with value: $it")
            }

            tealium.visitorService?.let { vs ->
                vs.onVisitorIdUpdated.subscribe(::onVisitorIdUpdated)
                vs.resetVisitorId()
            }

            // do onReady
            Log.d("TealiumHelper", "Tealium is ready")
//            it.consent.consentStatus = ConsentStatus.Consented
//            it.consent.consentStatus = ConsentStatus.NotConsented
//
//            it.track(Dispatch("testEvent", TealiumDispatchType.Event))
//            it.track(
//                Dispatches.event("testEvent")
//                    .putContextData(TealiumBundle.create {
//                        put("key", "value")
//                    })
//                    .build()
//            )
        }
    }

    fun track(
        event: String,
        type: TealiumDispatchType = TealiumDispatchType.Event,
        data: TealiumBundle = TealiumBundle.EMPTY_BUNDLE
    ) {
        shared?.track(Dispatch.create(event, type, data), ::onTracked)
    }

    override fun onDispatchDropped(dispatch: Dispatch) {
        Log.d("TealiumHelper", "Dispatch dropped ${dispatch.payload()}")
    }

    override fun onDispatchQueued(dispatch: Dispatch) {
        Log.d("TealiumHelper", "Dispatch queued ${dispatch.payload()}")
    }

    override fun onDispatchReady(dispatch: Dispatch) {
        Log.d("TealiumHelper", "Dispatch ready ${dispatch.payload()}")
    }

    override fun onConsentStatusUpdated(status: ConsentStatus) {
        Log.d("TealiumHelper", "Status: $status")
    }

    private fun onTracked(dispatch: Dispatch, status: TrackResult) {
        Log.d("TealiumHelper", "ProcessingStatus: ${dispatch.tealiumEvent} - ${status::class.simpleName}")
    }
}

class CustomInterceptor(private val delayInterval: Long) : Interceptor {
    override fun didComplete(request: HttpRequest, result: NetworkResult) {
        when (result) {
            is Success -> println("Successful request : ${result.httpResponse.statusCode}")
            is Failure -> {
                when (val error = result.networkError) {
                    is Non200Error -> println("Failed request with status code: ${error.statusCode}")
                    is IOError -> println("Failed request - cause: ${error.ex?.cause}, message: ${error.ex?.message}")
                    is UnexpectedError -> println("Failed request - cause: ${error.ex?.cause}, message: ${error.ex?.message}")
                    is Cancelled -> println("Failed request - cancelled")
                }
            }
        }
    }

    override fun shouldRetry(
        request: HttpRequest,
        result: NetworkResult,
        retryCount: Int
    ): RetryPolicy {
        return when (result) {
            is Failure -> {
                if (result.networkError.isRetryable()) {
                    RetryAfterDelay(delayInterval)
                } else {
                    // something else
                    DoNotRetry
                }
            }

            else -> DoNotRetry
        }
    }
}
