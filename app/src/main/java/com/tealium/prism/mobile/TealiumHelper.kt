package com.tealium.prism.mobile

import android.app.Application
import android.util.Log
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.key
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.logIfInfoEnabled
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Environment
import com.tealium.prism.core.api.misc.ExpiryPolicy
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.Interceptor
import com.tealium.prism.core.api.network.NetworkException.CancelledException
import com.tealium.prism.core.api.network.NetworkException.NetworkIOException
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkException.UnexpectedException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.api.network.RetryPolicy.RetryAfterDelay
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.onFailure
import com.tealium.prism.core.api.pubsub.onSuccess
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.extensions.api.lowercase.LowercaseSettingsBuilder
import com.tealium.prism.extensions.api.persistdatavalue.PersistDataValueSettingsBuilder
import com.tealium.prism.extensions.api.persistdatavalue.UpdatePolicy
import com.tealium.prism.extensions.api.setdatavalues.SetDataValuesSettingsBuilder
import com.tealium.prism.jstransformer.JavaScriptTransformationSettingsBuilder
import com.tealium.prism.lifecycle.lifecycle
import com.tealium.prism.momentsapi.MomentsApiRegion
import com.tealium.prism.mobile.ExampleCmpAdapter.Purposes
import com.tealium.prism.momentsapi.momentsApi

object TealiumHelper {

    private const val TAG = "TealiumHelper"

    var shared: Tealium? = null
        private set

    lateinit var cmp: MutableCmpAdapter

    val isEnabled: Boolean get() = shared != null

    fun init(application: Application, onReady: ((TealiumResult<Tealium>) -> Unit)? = null) {
        // Initialize and store the CmpAdapter
        cmp = ExampleCmpAdapter(application.applicationContext)

        val config = TealiumConfig.Builder(
            application = application,
            modules = configureModules(),
            accountName = "tealiummobile",
            profileName = "demo",
            environment = Environment.DEV
        ).configureCoreSettings { settings ->
            settings
                .setLogLevel(LogLevel.TRACE)
        }.setSettingsFile("tealium-settings.jsonc")
            .enableConsentIntegration(cmp) { settings ->
                settings
                    .setTealiumPurposeId(Purposes.TEALIUM)
                    .addPurpose(Purposes.TRACKING, setOf(Modules.Types.COLLECT))
                    .addPurpose(Purposes.FUNCTIONAL, setOf("logger"))
                    .setRefireDispatcherIds(setOf(Modules.Types.COLLECT))
            }
            .addTransformation(
                SetDataValuesSettingsBuilder("sdv_1")
                    .addScope(TransformationScope.AfterCollectors)
                    .setConstant("some_value".asDataItem(), key("some_key"))
                    .setFrom(key("some_key"), key("other_key"))
                    .build()
            )
            .addTransformation(
                JavaScriptTransformationSettingsBuilder("js_1")
                    .addScope(TransformationScope.AfterCollectors)
                    .setJsCode("payload.isVip = true")
                    .build()
            )
            .addTransformation(
                LowercaseSettingsBuilder("lowercase-specific")
                    .addScope(TransformationScope.AllDispatchers)
                    .lowercaseVariables(
                            listOf(
                                key("event_name"),
                                key("event_category"),
                                key("event_label"),
                                key("user_id")
                            )
                    )
                    .build()
            )

//            .addTransformation(
//                LowercaseSettingsBuilder("lowercase-all-variables")
//                    .addScope(TransformationScope.AllDispatchers)
//                    .lowercaseAllVariables()
//                    .build()
//            )

//            .addTransformation(PersistDataValueSettingsBuilder("pdv_1")
//                .addScope(TransformationScope.AfterCollectors)
//                .persistConstant("persisted_value", path(JsonPath["persist_object"]["persist_key"]))
//                .setExpiryPolicy(ExpiryPolicy.FOREVER)
//                .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
//                .build()
//            )

            .addTransformation(
                PersistDataValueSettingsBuilder("pdv_2")
                .addScope(TransformationScope.AfterCollectors)
                .persistFrom(key("persisted_value"), key("first_persisted_value"))
                .setExpiryPolicy(ExpiryPolicy.duration(10.minutes))
                .setUpdatePolicy(UpdatePolicy.KEEP_FIRST_VALUE)
                .build()
            )

//            .addBarrier(Barriers.connectivity(),
//                setOf(
//                    BarrierScope.Dispatcher(Modules.Types.COLLECT),
//                    BarrierScope.Dispatcher("logger")
//                )
//            )
//            .addBarrier(Barriers.batching(),
//                setOf(
//                    BarrierScope.Dispatcher(Modules.Types.COLLECT),
//                    BarrierScope.Dispatcher("logger")
//                )
//            )

        shared = Tealium.create(config.build()) { result ->
            val tealium = result.getOrNull() ?: return@create

            tealium.dataLayer.onDataUpdated.subscribe(::onDataUpdated)
            tealium.dataLayer.onDataRemoved.subscribe(::onDataRemoved)
            tealium.dataLayer.onDataRemoved.subscribe {
                it.forEach {
                    Log.d("Lambda", "Removed: key: $it")
                }
            }

            tealium.dataLayer.transactionally { editor ->
                editor
                    .put("key", "value".asDataItem(), Expiry.SESSION)
                    .put("string", "value".asDataItem(), Expiry.SESSION)
                    .put("int", 1.asDataItem(), Expiry.SESSION)
                    .put("bool", true.asDataItem(), Expiry.SESSION)
                    .put("array", listOf(1, 2, 3).asDataList(), Expiry.SESSION)
                    .put("object", mapOf("key" to "value").asDataObject(), Expiry.SESSION)
                    .commit()
            }.onFailure {
                Log.d("DataLayer", "Transactional update failed: ${it.message}")
            }

            Log.d(TAG, "Tealium is ready")
            onReady?.invoke(result)
        }
    }

    fun joinTrace(traceId: String) {
        shared?.trace?.join(traceId)
    }

    fun leaveTrace() {
        shared?.trace?.leave()
    }

    fun endVisitorSession() {
        shared?.trace?.forceEndOfVisit()
    }

    fun shutdown() {
        shared?.shutdown()
        shared = null
    }

    fun track(
        event: String,
        type: DispatchType = DispatchType.Event,
        data: DataObject = DataObject.EMPTY_OBJECT
    ) {
        shared?.apply {
            track(event, type, data).onSuccess(::onTracked)

            dataLayer.transactionally { editor ->
                val sessionEvents = editor.getInt("count") ?: 1
                editor.put("count", sessionEvents + 1, Expiry.SESSION)
                    .commit()
            }
        }
    }

    fun flush() {
        shared?.flushEventQueue()
    }

    private fun configureModules(): List<ModuleFactory> {
        return listOf(
            configureCollect(),
            Modules.connectivityData(),
            Modules.appData(),
            Modules.deviceData(),
            Modules.deepLink(),
            configureTrace(),
            configureLifecycle(),
            configureMomentsApi(),
            configureLoggingDispatcher("logger")
        )
    }

    private fun configureTrace(): ModuleFactory {
        return Modules.trace()
//        return Modules.trace { settings ->
//            settings.setTrackErrors(true)
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

    private fun configureMomentsApi(): ModuleFactory {
        return Modules.momentsApi { settings ->
            settings
                .setRegion(MomentsApiRegion.UsEast)
//                .setReferrer("https://example.com")
        }
    }

    private fun configureLoggingDispatcher(id: String): ModuleFactory {
        return object : ModuleFactory {
            override val moduleType: String
                get() = id

            override fun getEnforcedSettings(): List<DataObject> =
                listOf(ModuleSettingsBuilder(id).build())

            override fun create(
                moduleId: String,
                context: TealiumContext,
                configuration: DataObject
            ): Module? {
                return object : Dispatcher {
                    override fun dispatch(
                        dispatches: List<Dispatch>,
                        callback: Callback<List<Dispatch>>
                    ): Disposable {
                        context.logger.logIfInfoEnabled(id) {
                            "Audit: Dispatched ${dispatches.map(Dispatch::logDescription)}"
                        }
                        callback.onComplete(dispatches)
                        return Disposables.disposed()
                    }
                    override val id: String
                        get() = id
                    override val version: String
                        get() = "1.0.0"
                }
            }
        }
    }

    private fun onTracked(status: TrackResult) {
        Log.d(TAG, "ProcessingStatus: ${status.description}")
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
