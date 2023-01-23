package com.tealium.core.internal

import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.*
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.ModuleSettingImpl
import java.lang.ref.WeakReference

class TealiumImpl(
    private val config: TealiumConfig,
    private val onReady: Tealium.OnTealiumReady
) : Tealium {

    private val moduleManager = ModuleManagerImpl(
        config.modules, TealiumContext(config.application, this), SdkSettings(
            mapOf("Example" to ModuleSettingImpl())
        )
    )

    private val _trace = TraceManagerImpl()
    private val _deeplink = DeeplinkManagerImpl()
    private val _timedEvents = TimedEventsManagerImpl()
    private val _dataLayer = DataLayerImpl()
    private val _consent = ConsentManagerImpl()
    // TODO - create async? + push into modulesmanager

    override val trace: TraceManager
        get() = TraceManagerWrapper(
            WeakReference(moduleManager)
        )

    override val deeplink: DeeplinkManager
        get() = DeepLinkManagerWrapper(
            WeakReference(moduleManager)
        )
    override val timedEvents: TimedEventsManager
        get() = TimedEventsManagerWrapper(
            WeakReference(moduleManager)
        )
    override val dataLayer: DataLayer
        get() = DataLayerWrapper(
            WeakReference(moduleManager)
        )
    override val consent: ConsentManager
        get() = ConsentManagerWrapper(
            WeakReference(moduleManager)
        )

    override fun track(dispatch: Dispatch) {
        val copy = Dispatch(dispatch)

        // collection
        moduleManager.getModulesOfType(Collector::class.java).forEach {
            copy.addAll(it.collect())
        }

        // Transform
        // todo

        // Validation
        // todo

        // Dispatch
        moduleManager.getModulesOfType(Dispatcher::class.java).forEach { dispatcher ->
            dispatcher.dispatch(
                listOf(copy)
                // TODO - this might have been queued/batched.
            )
        }
    }

    override fun flushEventQueue() {
        TODO("Not yet implemented")
    }

    fun shutdown() {
        TODO("Not yet implemented")
    }

    init {
        onReady.onReady(this)
    }
}