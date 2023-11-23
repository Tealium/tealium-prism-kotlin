package com.tealium.mobile

import android.app.Application
import android.util.Log
import com.tealium.core.*
import com.tealium.core.api.ConsentStatus
import com.tealium.core.api.DataLayer
import com.tealium.core.api.Dispatch
import com.tealium.core.api.VisitorService
import com.tealium.core.api.listeners.ConsentStatusUpdatedListener
import com.tealium.core.api.listeners.DispatchDroppedListener
import com.tealium.core.api.listeners.DispatchQueuedListener
import com.tealium.core.api.listeners.DispatchReadyListener
import com.tealium.core.internal.network.*
import com.tealium.core.Modules
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.network.Cancelled
import com.tealium.core.api.network.DelayPolicy
import com.tealium.core.api.network.DoNotDelay
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

object TealiumHelper :
    DispatchReadyListener,
    DispatchQueuedListener,
    DispatchDroppedListener,
    ConsentStatusUpdatedListener,
//    DataLayer.DataLayerListener
    DataLayer.DataLayerUpdatedListener,
    DataLayer.DataLayerRemovedListener,
    VisitorService.VisitorIdUpdatedListener {

    private const val INSTANCE_NAME = "main"

    private val shared: Tealium?
        get() = Tealium[INSTANCE_NAME]

    override fun onVisitorIdUpdated(visitorId: String) {
        Log.d("Helper", "This Updated VisitorId: $visitorId")
    }

    override fun onDataUpdated(key: String, value: Any) {
        Log.d("Helper", "DataUpdated $key : $value")
    }

    override fun onDataRemoved(keys: Set<String>) {
        Log.d("Helper", "DataRemoved ${keys.joinToString(", ")}")
    }

    fun init(application: Application) {
        val config = TealiumConfig(
            application = application,
            modules = listOf(Modules.VisitorService, Modules.Collect),
            fileName = "tealium-settings.json",
            accountName = "tealiummobile",
            profileName = "android",
            environment = Environment.DEV
        )

        Tealium.create(INSTANCE_NAME, config) { tealium, error ->
//            it.events.subscribe(this)
//            it.track("", TealiumDispatchType.Event) {
//                put
//            }
            tealium.dataLayer.onDataUpdated.subscribe(this)
            tealium.dataLayer.onDataRemoved.subscribe(this)
            tealium.dataLayer.onDataRemoved.subscribe {
                it.forEach {
                    Log.d("Lambda", "Removed: key: $it")
                }
            }

            tealium.dataLayer.put("key", "value")
            tealium.dataLayer.put("key2", "value2")
            tealium.dataLayer.remove("key")
            tealium.dataLayer.remove("key2")

            tealium.visitorService?.let { vs ->
//                val vId = vs.visitorId.get()
//                Log.d("VisitorId", /**/"vId = $vId")
//                vs.visitorId.subscribe {
//                    Log.d("OnMain?", "Executing on ${Thread.currentThread().name}")
//                    Log.d("VisitorId", "Updated VisitorId: $it")
//                }
                vs.resetVisitorId()
                vs.resetVisitorId()
                vs.resetVisitorId()
                vs.resetVisitorId()
//                vs.visitorId.subscribe(this)
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
        shared?.track(Dispatch.create(event, type, data))
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
//        return RetryAfterDelay(delayInterval)
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

    override fun shouldDelay(request: HttpRequest): DelayPolicy {
        // if connectivity not available, then delay
        return DoNotDelay
    }
}