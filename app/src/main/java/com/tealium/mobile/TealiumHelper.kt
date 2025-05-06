package com.tealium.mobile

import android.app.Application
import android.util.Log
import com.tealium.core.api.Modules
import com.tealium.core.api.Tealium
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.Barriers
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemUtils.asDataItem
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.logger.logIfInfoEnabled
import com.tealium.core.api.misc.Environment
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
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
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.onFailure
import com.tealium.core.api.pubsub.onSuccess
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.internal.logger.logDescriptions
import com.tealium.core.internal.modules.collect.CollectDispatcher
import com.tealium.core.internal.pubsub.CompletedDisposable
import com.tealium.lifecycle.LifecycleDataTarget
import com.tealium.lifecycle.lifecycle

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
            localSdkSettingsFileName = "tealium-settings.json"

//            addBarrier(Barriers.connectivity(),
//                setOf(
//                    BarrierScope.Dispatcher(CollectDispatcher.moduleName),
//                    BarrierScope.Dispatcher("logger")
//                )
//            )
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
                editor.put("key", "value".asDataItem(), Expiry.SESSION)
                    .put("key2", "value2".asDataItem(), Expiry.SESSION)
                    .remove("key2")
                    .commit()
            }.onFailure {
                Log.d("DataLayer", "Transactional update failed: ${it.message}")
            }
            tealium.dataLayer.get("key").onSuccess {
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
            Modules.deviceDataCollector(),
            configureLifecycle(),
            configureLoggingDispatcher("logger")
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

    private fun configureLifecycle(): ModuleFactory {
        return Modules.lifecycle()
//        return Modules.lifecycle { settings ->
//            settings
//                .setSessionTimeoutInMinutes(10)
//                .setDataTarget(LifecycleDataTarget.AllEvents)
//        }
    }

    private fun configureLoggingDispatcher(id: String): ModuleFactory {
        return object : ModuleFactory {
            override val id: String
                get() = id

            override fun create(context: TealiumContext, configuration: DataObject): Module? {
                return object : Dispatcher {
                    override fun dispatch(
                        dispatches: List<Dispatch>,
                        callback: TealiumCallback<List<Dispatch>>
                    ): Disposable {
                        context.logger.logIfInfoEnabled(id) {
                            "Audit: Dispatched ${dispatches.logDescriptions()}"
                        }
                        return CompletedDisposable
                    }

                    override val id: String
                        get() = id
                    override val version: String
                        get() = "1.0.0"
                }
            }
        }
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