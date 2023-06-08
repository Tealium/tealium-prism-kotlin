package com.tealium.core.internal

import com.tealium.core.Environment
import com.tealium.core.LogLevel
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.*
import com.tealium.core.internal.observables.ObservablesFactoryImpl
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.listeners.Listener
import com.tealium.core.internal.messengers.DispatchDroppedMessenger
import com.tealium.core.internal.messengers.DispatchQueuedMessenger
import com.tealium.core.internal.messengers.DispatchReadyMessenger
import com.tealium.core.internal.messengers.DispatchSendMessenger
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.TealiumCollector
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class TealiumImpl(
    private val config: TealiumConfig,
    private val onReady: Tealium.OnTealiumReady? = null
) : Tealium {

    private val backgroundService = Executors.newSingleThreadScheduledExecutor()
    private val eventRouter = EventDispatcher<Listener>(backgroundService)
    private val logger: Logger = Logger(logLevel = LogLevel.DEV) // todo read level from file?
    private val messengerService: MessengerServiceImpl = MessengerServiceImpl(eventRouter)

    override val events: Subscribable<Listener>
        get() = messengerService

    private val tealiumContext: TealiumContext =
        TealiumContext(
            config.application,
            // TODO - read from file instead.
            coreSettings = CoreSettingsImpl("tealiummobile", "demo", Environment.DEV),
            dataLayer = dataLayer,
            events = messengerService,
            logger = logger,
            visitorId = "", // TODO
            observables = ObservablesFactoryImpl(backgroundService),
            tealium = this
        )

    private var _moduleManager = ModuleManagerImpl( // empty module manager.
        tealiumContext, SdkSettings(emptyMap()), emptyList()
    )

    override val modules: ModuleManager
        get() = _moduleManager

    init {
        backgroundService.submit {
            bootstrap()
        }
    }

    private fun bootstrap() {

        val modules: MutableList<ModuleFactory> = mutableListOf(
            TealiumCollector,
            DataLayerImpl,
            TraceManagerImpl,
            DeeplinkManagerImpl,
            TimedEventsManagerImpl,
            InternalModuleFactories.consentManagerFactory(eventRouter),
        )

        modules.addAll(config.modules)

        try {
            _moduleManager = ModuleManagerImpl(
                tealiumContext, SdkSettings(emptyMap()), modules
            )
        } catch (ex: Exception) {
            logger.error("bootstrap", "" + ex.message)
        }

        // todo - might have queued incoming events + dispatch them now.
        onReady?.onReady(this)
    }

    override val trace: TraceManager
        get() = TraceManagerWrapper(
            WeakReference(_moduleManager)
        )

    override val deeplink: DeeplinkManager
        get() = DeepLinkManagerWrapper(
            WeakReference(_moduleManager)
        )
    override val timedEvents: TimedEventsManager
        get() = TimedEventsManagerWrapper(
            WeakReference(_moduleManager)
        )
    override val dataLayer: DataLayer
        get() = DataLayerWrapper(
            WeakReference(_moduleManager)
        )
    override val consent: ConsentManager
        get() = ConsentManagerWrapper(
            WeakReference(_moduleManager)
        )

    @Suppress("NAME_SHADOWING")
    override fun track(dispatch: Dispatch) {
        Dispatch.create(dispatch).let { dispatch ->
            // collection
            val builder = TealiumBundle.Builder()
            _moduleManager.getModulesOfType(Collector::class.java).forEach {
                builder.putAll(it.collect())
            }
            dispatch.addAll(builder.getBundle())

            // Transform
            _moduleManager.getModulesOfType(Transformer::class.java).forEach {
                it.transform(dispatch)
            }

            logger.debug("TealiumImpl") {
                "Dispatch(${dispatch.id.substring(0, 5)}) Ready - ${dispatch.payload()}"
            }
            eventRouter.send(DispatchReadyMessenger(dispatch))

            // Validation
            // todo

//        if (false) { // TODO - if queued
            eventRouter.send(DispatchQueuedMessenger(dispatch))
//        } else if (false) { // TODO - if dropped
            eventRouter.send(DispatchDroppedMessenger(dispatch))
//        }

            // Dispatch
            // TODO - this might have been queued/batched.
            val dispatches = listOf(dispatch)
            _moduleManager.getModulesOfType(Dispatcher::class.java).forEach { dispatcher ->
                dispatcher.dispatch(
                    dispatches,
                )
            }

            eventRouter.send(DispatchSendMessenger(dispatches))
        }
    }

    override fun flushEventQueue() {
        TODO("Not yet implemented")
    }

    fun shutdown() {
        TODO("Not yet implemented")
    }
}