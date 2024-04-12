package com.tealium.core.internal

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.ActivityManager
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.PersistenceException
import com.tealium.core.api.Scheduler
import com.tealium.core.api.Schedulers
import com.tealium.core.api.listeners.TrackResultListener
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.internal.dispatch.BarrierCoordinatorImpl
import com.tealium.core.internal.dispatch.BarrierScope
import com.tealium.core.internal.dispatch.DispatchManagerImpl
import com.tealium.core.internal.dispatch.ScopedBarrier
import com.tealium.core.internal.dispatch.TransformerCoordinatorImpl
import com.tealium.core.internal.dispatch.VolatileQueueManagerImpl
import com.tealium.core.internal.modules.CollectDispatcher
import com.tealium.core.internal.modules.InternalModuleManager
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.TealiumCollector
import com.tealium.core.internal.network.ConnectivityBarrier
import com.tealium.core.internal.network.ConnectivityInterceptor
import com.tealium.core.internal.network.ConnectivityRetriever
import com.tealium.core.internal.network.HttpClient
import com.tealium.core.internal.network.NetworkHelperImpl
import com.tealium.core.internal.observables.CompositeDisposable
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.addTo
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.FileDatabaseProvider
import com.tealium.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.core.internal.persistence.ModulesRepository
import com.tealium.core.internal.persistence.SQLModulesRepository
import com.tealium.core.internal.settings.SettingsManager
import java.io.File
import java.io.IOException
import java.util.UUID

class TealiumImpl(
    private val config: TealiumConfig,
    private val dbProvider: DatabaseProvider = FileDatabaseProvider(config),
    private val tealiumScheduler: Scheduler,
    private val networkScheduler: Scheduler,
    val moduleManager: InternalModuleManager = ModuleManagerImpl(
        getDefaultModules() + config.modules,
        tealiumScheduler
    ),
    private val activityManager: ActivityManager = ActivityManagerProxy()
) {
    private val schedulers: Schedulers
    private val settings: StateSubject<SdkSettings>
    private val logger: Logger
    private val networkUtilities: NetworkUtilities
    private val dispatchManager: DispatchManagerImpl
    private val tracker: TrackerImpl
    private val tealiumContext: TealiumContext
    private val disposables: CompositeDisposable = DisposableContainer()

    init {
        val directoryCreated = makeTealiumDirectory(config)
        if (!directoryCreated) {
            throw PersistenceException("Failed to create Tealium directory", IOException())
        }

        try {
            // TODO - consider moving creation into TealiumProxy instead, and injecting this
            val db = dbProvider.database
        } catch (e: Exception) {
            throw PersistenceException("Database Initialization failed.", e)
        }

        val modulesRepository = SQLModulesRepository(dbProvider)
        val storage = ModuleStoreProviderImpl(dbProvider, modulesRepository)

        val sharedDataStore = storage.getSharedDataStore()
        settings =
            Observables.stateSubject(SettingsManager.loadInitialSettings(config, sharedDataStore))

        logger = LoggerImpl(
            logHandler = config.logHandler,
            onSdkSettingsUpdated = settings,
        )

        // TODO - clear session data if necessary
        logger.info?.log(BuildConfig.TAG, "Clearing expired data.")
        modulesRepository.deleteExpired(ModulesRepository.ExpirationType.UntilRestart)

        schedulers = SchedulersImpl(
            tealium = tealiumScheduler,
            io = networkScheduler
        )
        networkUtilities = createNetworkUtilities(config, logger, schedulers)
        val settingsManager = SettingsManager(
            config,
            networkUtilities.networkHelper,
            sharedDataStore,
            logger,
        )

        dispatchManager = DispatchManagerImpl(
            consentManager = com.tealium.core.internal.consent.ConsentManagerImpl(),
            // TODO - Load default barriers
            barrierCoordinator = BarrierCoordinatorImpl(
                setOf(
                    ConnectivityBarrier(ConnectivityRetriever.getInstance(config.application).onConnectionStatusUpdated)
                ), setOf(
                    ScopedBarrier(ConnectivityBarrier.BARRIER_ID, setOf(BarrierScope.Dispatcher(CollectDispatcher.moduleName)))
                )
            ),
            // TODO - load transformers
            transformerCoordinator = TransformerCoordinatorImpl(
                setOf(),
                Observables.stateSubject(setOf()),
                tealiumScheduler
            ),
            // TODO - create flow from the ModulesManager
            dispatchers = moduleManager.modules
                .map { it.filterIsInstance(Dispatcher::class.java).toSet() },
            // TODO - hook this up to persistent storage
            queueManager = VolatileQueueManagerImpl(),
            logger = logger
        )
        tracker = TrackerImpl(moduleManager, dispatchManager, logger)

        tealiumContext =
            TealiumContext(
                config.application,
                config,
                logger = logger,
                // TODO - Visitor Storage not done yet, but EventStream requires a value for tealium_visitor_id
                visitorId = UUID.randomUUID().toString().replace("-", ""),
                storageProvider = ModuleStoreProviderImpl(
                    dbProvider, modulesRepository
                ),
                network = networkUtilities,
                settingsProvider = settingsManager,
                tracker = tracker,
                schedulers = schedulers,
                activityManager = activityManager
            )

        moduleManager.updateModuleSettings(tealiumContext, settings.value)

        settingsManager.refreshRemote()
        settingsManager.subscribeToActivityUpdates()
            .addTo(disposables)
        dispatchManager.startDispatchLoop()

        logger.debug?.log(BuildConfig.TAG, "Bootstrap complete, continue to onReady")
        // todo - might have queued incoming events + dispatch them now.
    }

    fun track(dispatch: Dispatch) {
        trackInternal(dispatch, null)
    }

    fun track(dispatch: Dispatch, onComplete: TrackResultListener) {
        trackInternal(dispatch, onComplete)
    }

    private fun trackInternal(dispatch: Dispatch, onComplete: TrackResultListener?) {
        Dispatch.create(dispatch).let { copy ->
            tracker.track(copy, onComplete)
        }
    }

    fun flushEventQueue() {
        TODO("Not yet implemented")
    }

    fun shutdown() {
        logger.info?.log("Tealium", "Shutting down.")

        disposables.dispose()
        dispatchManager.stopDispatchLoop()
    }

    companion object {

        fun getDefaultModules(): List<ModuleFactory> {
            return listOf(
                TealiumCollector,
                DataLayerImpl,
                TraceManagerImpl,
                DeeplinkManagerImpl,
                TimedEventsManagerImpl,
                ConsentManagerImpl.Factory,
            )
        }

        fun createNetworkUtilities(
            config: TealiumConfig,
            logger: Logger,
            schedulers: Schedulers
        ): NetworkUtilities {
            val networkClient = HttpClient(
                logger = logger,
                schedulers.tealium,
                schedulers.io,
                interceptors = mutableListOf(ConnectivityInterceptor.getInstance(config.application))
            )
            return NetworkUtilities(
                connectivity = ConnectivityRetriever.getInstance(config.application),
                networkClient = networkClient,
                networkHelper = NetworkHelperImpl(networkClient, logger)
            )
        }

        fun makeTealiumDirectory(config: TealiumConfig): Boolean {
            val pathName =
                "${config.application.filesDir}${File.separatorChar}tealium${File.separatorChar}${config.accountName}${File.separatorChar}${config.profileName}${File.separatorChar}${config.environment.environment}"
            val tealiumDirectory = File(pathName)

            return try {
                tealiumDirectory.exists() || tealiumDirectory.mkdirs()
            } catch (e: IOException) {
                false
            }
        }
    }
}