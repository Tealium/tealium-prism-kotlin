package com.tealium.prism.lifecycle.internal

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.lifecycle.BuildConfig
import com.tealium.prism.lifecycle.InvalidEventOrderException
import com.tealium.prism.lifecycle.Lifecycle
import com.tealium.prism.lifecycle.LifecycleDataTarget
import com.tealium.prism.lifecycle.LifecycleEvent
import com.tealium.prism.lifecycle.LifecycleSettingsBuilder
import com.tealium.prism.lifecycle.ManualTrackingException

class LifecycleWrapper(
    private val moduleProxy: ModuleProxy<LifecycleModule>
) : Lifecycle {
    constructor(tealium: Tealium) : this(
        tealium.createModuleProxy(LifecycleModule::class.java)
    )

    override fun launch(dataObject: DataObject): Single<TealiumResult<Unit>> =
        moduleProxy.executeModuleTask { lifecycleModule ->
            lifecycleModule.launch(dataObject)
        }

    override fun launch(): Single<TealiumResult<Unit>> =
        launch(DataObject.EMPTY_OBJECT)

    override fun wake(dataObject: DataObject): Single<TealiumResult<Unit>> =
        moduleProxy.executeModuleTask { lifecycleModule ->
            lifecycleModule.wake(dataObject)
        }

    override fun wake(): Single<TealiumResult<Unit>> =
        wake(DataObject.EMPTY_OBJECT)

    override fun sleep(dataObject: DataObject): Single<TealiumResult<Unit>> =
        moduleProxy.executeModuleTask { lifecycleModule ->
            lifecycleModule.sleep(dataObject)
        }

    override fun sleep(): Single<TealiumResult<Unit>> =
        sleep(DataObject.EMPTY_OBJECT)
}

class LifecycleModule(
    private var lifecycleConfiguration: LifecycleConfiguration,
    private val lifecycleService: LifecycleService,
    private val applicationStatus: Observable<ActivityManager.ApplicationStatus>,
    private val tracker: Tracker,
    private val logger: Logger
) : Collector {

    constructor(
        context: TealiumContext,
        lifecycleConfiguration: LifecycleConfiguration,
        lifecycleService: LifecycleService
    ) : this(
        lifecycleConfiguration,
        lifecycleService,
        context.activityManager.applicationStatus,
        context.tracker,
        context.logger
    )

    internal var activitiesSubscription: Disposable? = null
    private var lastBackground: Long? = null
    internal var hasLaunched: Boolean = false
    internal var shouldSkipNextForeground: Boolean = false

    private val isInfiniteSession: Boolean
        get() = lifecycleConfiguration.sessionTimeoutInMinutes <= LifecycleDefault.INFINITE_SESSION

    init {
        activitiesSubscription = subscribeToActivityUpdates()
    }

    fun launch(dataObject: DataObject? = null) {
        if (lifecycleConfiguration.autoTrackingEnabled) {
            throw ManualTrackingException("Lifecycle auto-tracking is enabled, cannot manually track lifecycle event.")
        }

        registerLifecycleEvent(LifecycleEvent.Launch, System.currentTimeMillis(), dataObject)
    }

    fun wake(dataObject: DataObject? = null) {
        if (lifecycleConfiguration.autoTrackingEnabled) {
            throw ManualTrackingException("Lifecycle auto-tracking is enabled, cannot manually track lifecycle event.")
        }

        registerLifecycleEvent(LifecycleEvent.Wake, System.currentTimeMillis(), dataObject)
    }

    fun sleep(dataObject: DataObject? = null) {
        if (lifecycleConfiguration.autoTrackingEnabled) {
            throw ManualTrackingException("Lifecycle auto-tracking is enabled, cannot manually track lifecycle event.")
        }

        registerLifecycleEvent(LifecycleEvent.Sleep, System.currentTimeMillis(), dataObject)
    }

    internal fun registerLifecycleEvent(
        event: LifecycleEvent,
        timestamp: Long,
        dataObject: DataObject?
    ) {
        try {
            if (!isAcceptableEvent(event)) {
                throw InvalidEventOrderException("Invalid lifecycle event order. The event will not be processed.")
            }

            val state = when (event) {
                LifecycleEvent.Launch -> lifecycleService.registerLaunch(timestamp)
                LifecycleEvent.Wake -> lifecycleService.registerWake(timestamp)
                LifecycleEvent.Sleep -> lifecycleService.registerSleep(timestamp)
            }

            if (isTrackableEvent(event)) {
                val eventData = dataObject?.let { data ->
                    state.copy {
                        putAll(data)
                    }
                } ?: state

                val dispatch =
                    Dispatch.create(event.event, DispatchType.Event, eventData)
                tracker.track(dispatch, DispatchContext.Source.module(this::class.java))
            }

            if (event == LifecycleEvent.Launch && !hasLaunched) {
                hasLaunched = true
            }
        } catch (e: Exception) {
            logger.error(
                id,
                "Failed to process lifecycle event: $event.\nError: ${e.message}"
            )

            throw e
        }
    }

    private fun isTrackableEvent(event: LifecycleEvent): Boolean {
        return lifecycleConfiguration.trackedLifecycleEvents.contains(event)
    }

    private fun subscribeToActivityUpdates(): Disposable {
        return applicationStatus.filter { lifecycleConfiguration.autoTrackingEnabled }
            .subscribe { newStatus ->
                if (!hasLaunched) {
                    handleFirstLaunch(newStatus)
                } else {
                    handleApplicationStatus(newStatus)
                }
            }
    }

    private fun handleFirstLaunch(status: ActivityManager.ApplicationStatus) {
        val currentTimestamp = System.currentTimeMillis()
        when (status) {
            is ActivityManager.ApplicationStatus.Init -> {
                handleApplicationStatus(status)
                shouldSkipNextForeground = true
            }

            is ActivityManager.ApplicationStatus.Foregrounded -> {
                handleApplicationStatus(
                    ActivityManager.ApplicationStatus.Init(
                        currentTimestamp
                    )
                )
            }

            is ActivityManager.ApplicationStatus.Backgrounded -> {
                handleApplicationStatus(
                    ActivityManager.ApplicationStatus.Init(
                        currentTimestamp
                    )
                )
                handleApplicationStatus(
                    ActivityManager.ApplicationStatus.Backgrounded(
                        currentTimestamp
                    )
                )
            }
        }
    }

    private fun handleApplicationStatus(status: ActivityManager.ApplicationStatus) {
        if (status is ActivityManager.ApplicationStatus.Foregrounded && shouldSkipNextForeground) {
            shouldSkipNextForeground = false
            return
        }

        val dataObject = DataObject.create {
            put(StateKey.AUTOTRACKED, true)
        }

        when (status) {
            is ActivityManager.ApplicationStatus.Init -> {
                registerLifecycleEvent(
                    LifecycleEvent.Launch,
                    status.timestamp,
                    dataObject
                )
            }

            is ActivityManager.ApplicationStatus.Foregrounded -> {

                val timeDifferenceInMillis = lastBackground?.let { bg ->
                    status.timestamp - bg
                } ?: 0

                if (!isInfiniteSession && isExpiredSession(timeDifferenceInMillis)) {
                    registerLifecycleEvent(
                        LifecycleEvent.Launch,
                        status.timestamp,
                        dataObject
                    )

                } else {
                    registerLifecycleEvent(
                        LifecycleEvent.Wake,
                        status.timestamp,
                        dataObject
                    )
                }
            }

            is ActivityManager.ApplicationStatus.Backgrounded -> {
                lastBackground = status.timestamp

                registerLifecycleEvent(
                    LifecycleEvent.Sleep,
                    status.timestamp,
                    dataObject
                )

                if (shouldSkipNextForeground) {
                    shouldSkipNextForeground = false
                }
            }
        }
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        if (dispatchContext.source.isFromModule(this::class.java)
            || lifecycleConfiguration.dataTarget != LifecycleDataTarget.AllEvents
        ) {
            return DataObject.EMPTY_OBJECT
        }

        return lifecycleService.getCurrentState(System.currentTimeMillis())
    }

    override fun updateConfiguration(configuration: DataObject): Module? {
        lifecycleConfiguration = LifecycleConfiguration.fromDataObject(configuration)

        return this
    }

    override fun onShutdown() {
        activitiesSubscription?.dispose()
        activitiesSubscription = null
    }

    private fun isExpiredSession(timeElapsed: Long): Boolean {
        return timeElapsed > minutesToMillis(lifecycleConfiguration.sessionTimeoutInMinutes)
    }

    private fun minutesToMillis(minutes: Int): Long {
        return (minutes * 60 * 1000).toLong()
    }

    private fun isAcceptableEvent(event: LifecycleEvent): Boolean {
        val lastEvent = lifecycleService.lastLifecycleEvent
        var result = true
        when (event) {
            LifecycleEvent.Launch -> {
                if (hasLaunched && lastEvent != LifecycleEvent.Sleep) {
                    result = false
                }
            }

            LifecycleEvent.Sleep -> {
                if (lastEvent != LifecycleEvent.Wake && lastEvent != LifecycleEvent.Launch) {
                    result = false
                }
            }

            LifecycleEvent.Wake -> {
                if (lastEvent != LifecycleEvent.Sleep) {
                    result = false
                }
            }
        }
        return result
    }

    class Factory(
        enforcedSettings: DataObject? = null
    ) : ModuleFactory {

        constructor(moduleSettings: LifecycleSettingsBuilder) : this(moduleSettings.build())

        private val enforcedSettings = enforcedSettings?.let { listOf(it) }
            ?: emptyList()

        override val moduleType: String = Lifecycle.ID

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module? {
            val dataStore = context.storageProvider.getModuleStore(moduleId)
            return LifecycleModule(
                context,
                LifecycleConfiguration.fromDataObject(configuration),
                LifecycleServiceImpl(context, LifecycleStorageImpl(dataStore))
            )
        }
    }

    override val id: String = Lifecycle.ID
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION
}