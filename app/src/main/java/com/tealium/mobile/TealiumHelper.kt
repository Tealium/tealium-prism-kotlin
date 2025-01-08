package com.tealium.mobile

import android.app.Application
import android.util.Log
import com.tealium.core.api.Modules
import com.tealium.core.api.Tealium
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.misc.Environment
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkException.CancelledException
import com.tealium.core.api.network.NetworkException.NetworkIOException
import com.tealium.core.api.network.NetworkException.Non200Exception
import com.tealium.core.api.network.NetworkException.UnexpectedException
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Failure
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.api.network.RetryPolicy
import com.tealium.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.core.api.network.RetryPolicy.RetryAfterDelay
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.api.tracking.TrackResult

object TealiumHelper {

    private const val TAG = "TealiumHelper"

    var shared: Tealium? = null
        private set

    val isEnabled: Boolean get() = shared != null

    private fun onVisitorIdUpdated(visitorId: String) {
        Log.d("OnMain?", "Executing on ${Thread.currentThread().name}")
        Log.d("Helper", "This Updated VisitorId: $visitorId")
    }

    private fun onDataUpdated(dataObject: DataObject) {
        for (entry in dataObject) {
            onDataUpdated(entry.key, entry.value)
        }
    }

    private fun onDataUpdated(key: String, value: Any) {
        Log.d("Helper", "DataUpdated $key : $value")
    }

    private fun onDataRemoved(keys: List<String>) {
        Log.d("Helper", "DataRemoved ${keys.joinToString(", ")}")
    }

    fun init(application: Application, onReady: ((TealiumResult<Tealium>) -> Unit)? = null) {
        val config = TealiumConfig(
            application = application,
            modules = configureModules(),
            accountName = "tealiummobile",
            profileName = "android",
            environment = Environment.DEV
        ) { settings ->
            settings
                .setLogLevel(LogLevel.TRACE)
                .setBatchSize(8)
        }
        config.apply {
            useRemoteSettings = false
//            localSdkSettingsFileName = "tealium-settings.json"
        }

        shared = Tealium.create(config) { result ->
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

            tealium.dataLayer.transactionally { editor ->
                editor.put("key", DataItem.string("value"), Expiry.SESSION)
                    .put("key2", DataItem.string("value2"), Expiry.SESSION)
                    .remove("key2")
            }
            tealium.dataLayer.get("key") {
                Log.d("DataLayer", "Retrieved key with value: $it")
            }

            tealium.visitorService?.let { vs ->
                vs.onVisitorIdUpdated.subscribe(::onVisitorIdUpdated)
                vs.resetVisitorId()
            }

//            tealium.trace.killVisitorSession()
//                .subscribe {
//                    try {
//                        it.getOrThrow()
//                        Log.d(TAG, "Visitor Session Killed")
//                    } catch (e: TealiumException) {
//                        Log.d(TAG, "Error killing visitor session: ${e.message}")
//                    }
//                }

            // do onReady
            Log.d(TAG, "Tealium is ready")
//            it.consent.consentStatus = ConsentStatus.Consented
//            it.consent.consentStatus = ConsentStatus.NotConsented
//
//            it.track(Dispatch("testEvent", TealiumDispatchType.Event))
//            it.track(
//                Dispatches.event("testEvent")
//                    .putContextData(DataObject.create {
//                        put("key", "value")
//                    })
//                    .build()
//            )

            onReady?.invoke(result)
        }
    }

    fun shutdown() {
        shared?.shutdown()
        shared = null
    }

    fun track(
        event: String,
        type: TealiumDispatchType = TealiumDispatchType.Event,
        data: DataObject = DataObject.EMPTY_OBJECT
    ) {
        shared?.apply {
            track(Dispatch.create(event, type, data), ::onTracked)

            dataLayer.transactionally { editor ->
                val sessionEvents = editor.getInt("count") ?: 1
                editor.put("count", sessionEvents + 1, Expiry.SESSION)
                    .commit()
            }
        }

    }

    private fun onTracked(dispatch: Dispatch, status: TrackResult) {
        Log.d(
            TAG,
            "ProcessingStatus: ${dispatch.tealiumEvent} - ${status::class.simpleName}"
        )
    }

    private fun configureModules(): List<ModuleFactory> {
        return listOf(
//            configureConsent(),
            configureCollect(),
            Modules.connectivityCollector(),
            Modules.appDataCollector(),
            Modules.deviceDataCollector()
        )
    }

    private fun configureConsent(): ModuleFactory {
        // object should be subbed with actual cmp implementation.
        return Modules.consent(ExampleConsentManagementAdapter())
//        return Modules.consent(ExampleConsentManagementAdapter()) { settings ->
//            settings.setDispatcherToPurposes(mapOf("CollectDispatcher" to setOf("some_purpose")))
//        }
    }

    private fun configureCollect(): ModuleFactory {
        return Modules.collect()
//        return Modules.collect { settings ->
//            settings.setProfile("override_profile")
//        }
    }
}

class CustomInterceptor(private val delayInterval: Long) : Interceptor {
    override fun didComplete(request: HttpRequest, result: NetworkResult) {
        when (result) {
            is Success -> println("Successful request : ${result.httpResponse.statusCode}")
            is Failure -> {
                when (val error = result.networkException) {
                    is Non200Exception -> println("Failed request with status code: ${error.statusCode}")
                    is NetworkIOException -> println("Failed request - cause: ${error.cause?.cause}, message: ${error.cause?.message}")
                    is UnexpectedException -> println("Failed request - cause: ${error.cause?.cause}, message: ${error.cause?.message}")
                    is CancelledException -> println("Failed request - cancelled")
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
                if (result.networkException.isRetryable()) {
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