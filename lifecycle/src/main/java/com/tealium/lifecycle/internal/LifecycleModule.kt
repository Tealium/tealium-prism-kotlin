package com.tealium.lifecycle.internal

import com.tealium.core.api.Tealium
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.api.tracking.Tracker
import com.tealium.lifecycle.BuildConfig
import com.tealium.lifecycle.InvalidEventOrderException
import com.tealium.lifecycle.Lifecycle
import com.tealium.lifecycle.LifecycleDataTarget
import com.tealium.lifecycle.LifecycleEvent
import com.tealium.lifecycle.LifecycleSettingsBuilder
import com.tealium.lifecycle.ManualTrackingException

class LifecycleWrapper(
    private val moduleProxy: ModuleProxy<LifecycleModule>
) : Lifecycle {
    constructor(tealium: Tealium) : this(
        tealium.createModuleProxy(LifecycleModule::class.java)
    )

    override fun launch(dataObject: DataObject?, completion: TealiumCallback<TealiumException?>?) {
        handleEvent(completion) { lifecycleModule ->
            lifecycleModule.launch(dataObject)
        }
    }

    override fun wake(dataObject: DataObject?, completion: TealiumCallback<TealiumException?>?) {
        handleEvent(completion) { lifecycleModule ->
            lifecycleModule.wake(dataObject)
        }
    }

    override fun sleep(dataObject: DataObject?, completion: TealiumCallback<TealiumException?>?) {
        handleEvent(completion) { lifecycleModule ->
            lifecycleModule.sleep(dataObject)
        }
    }

    private fun handleEvent(
        completion: TealiumCallback<TealiumException?>?,
        task: (LifecycleModule) -> Unit
    ) {
        val result = moduleProxy.executeModuleTask(task)
        if (completion != null) {
            result.subscribe {
                val ex = it.exceptionOrNull()
                if (ex == null) {
                    completion.onComplete(null)
                    return@subscribe
                }

                val tealiumException = (ex as? TealiumException) ?: TealiumException(cause = ex)
                completion.onComplete(tealiumException)
            }
        }
    }
}

class LifecycleModule(
    private var lifecycleSettings: LifecycleSettings,
    private val lifecycleService: LifecycleService,
    private val applicationStatus: Observable<ActivityManager.ApplicationStatus>,
    private val tracker: Tracker,
    private val logger: Logger
) : Collector {

    constructor(
        context: TealiumContext,
        lifecycleSettings: LifecycleSettings,
        lifecycleService: LifecycleService
    ) : this(
        lifecycleSettings,
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
        get() = lifecycleSettings.sessionTimeoutInMinutes <= LifecycleDefault.INFINITE_SESSION

    init {
        activitiesSubscription = subscribeToActivityUpdates()
    }

    fun launch(dataObject: DataObject? = null) {
        if (lifecycleSettings.autoTrackingEnabled) {
            throw ManualTrackingException("Lifecycle auto-tracking is enabled, cannot manually track lifecycle event.")
        }

        registerLifecycleEvent(LifecycleEvent.Launch, System.currentTimeMillis(), dataObject)
    }

    fun wake(dataObject: DataObject? = null) {
        if (lifecycleSettings.autoTrackingEnabled) {
            throw ManualTrackingException("Lifecycle auto-tracking is enabled, cannot manually track lifecycle event.")
        }

        registerLifecycleEvent(LifecycleEvent.Wake, System.currentTimeMillis(), dataObject)
    }

    fun sleep(dataObject: DataObject? = null) {
        if (lifecycleSettings.autoTrackingEnabled) {
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
                    Dispatch.create(event.event, TealiumDispatchType.Event, eventData)
                tracker.track(dispatch)
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
        return lifecycleSettings.trackedLifecycleEvents.contains(event)
    }

    private fun subscribeToActivityUpdates(): Disposable {
        return applicationStatus.filter { lifecycleSettings.autoTrackingEnabled }
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

    // TODO: needs to filter for lifecycle events
    override fun collect(): DataObject {
        return if (lifecycleSettings.dataTarget == LifecycleDataTarget.AllEvents) {
            lifecycleService.getCurrentState(System.currentTimeMillis())
        } else DataObject.EMPTY_OBJECT
    }

    override fun updateSettings(moduleSettings: DataObject): Module? {
        lifecycleSettings = LifecycleSettings.fromDataObject(moduleSettings)

        return this
    }

    override fun onShutdown() {
        activitiesSubscription?.dispose()
        activitiesSubscription = null
    }

    private fun isExpiredSession(timeElapsed: Long): Boolean {
        return timeElapsed > minutesToMillis(lifecycleSettings.sessionTimeoutInMinutes)
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
        private val settings: DataObject? = null
    ) : ModuleFactory {
        constructor(moduleSettings: LifecycleSettingsBuilder) : this(moduleSettings.build())

        override val id: String
            get() = LifecycleSettings.MODULE_ID

        override fun getEnforcedSettings(): DataObject? = settings

        override fun create(context: TealiumContext, settings: DataObject): Module? {
            val dataStore = context.storageProvider.getModuleStore(this)
            return LifecycleModule(
                context,
                LifecycleSettings.fromDataObject(settings),
                LifecycleServiceImpl(context, LifecycleStorageImpl(dataStore))
            )
        }
    }

    override val id: String
        get() = LifecycleSettings.MODULE_ID
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION
}