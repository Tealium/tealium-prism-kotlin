package com.tealium.core.internal

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.ActivityManager
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.PersistenceException
import com.tealium.core.api.Scheduler
import com.tealium.core.api.Schedulers
import com.tealium.core.api.listeners.TrackResultListener
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.internal.IdentityUpdatedObserver.subscribeIdentityUpdates
import com.tealium.core.internal.consent.ConsentModule
import com.tealium.core.internal.dispatch.BarrierCoordinator
import com.tealium.core.internal.dispatch.BarrierCoordinatorImpl
import com.tealium.core.internal.dispatch.BarrierRegistryImpl
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.internal.dispatch.DispatchManagerImpl
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.internal.dispatch.QueueManagerImpl
import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.internal.dispatch.TransformerCoordinator
import com.tealium.core.internal.dispatch.TransformerCoordinatorImpl
import com.tealium.core.internal.dispatch.TransformerRegistryImpl
import com.tealium.core.internal.modules.CollectDispatcher
import com.tealium.core.internal.modules.InternalModuleFactories
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
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.addTo
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.FileDatabaseProvider
import com.tealium.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.core.internal.persistence.ModulesRepository
import com.tealium.core.internal.persistence.QueueRepository
import com.tealium.core.internal.persistence.SQLModulesRepository
import com.tealium.core.internal.persistence.SQLQueueRepository
import com.tealium.core.internal.persistence.TimeFrame
import com.tealium.core.internal.settings.CoreSettings
import com.tealium.core.internal.settings.SettingsManager
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    private val coreSettings: ObservableState<CoreSettings>
    private val logger: Logger
    private val networkUtilities: NetworkUtilities
    private val dispatchManager: DispatchManagerImpl
    private val tracker: TrackerImpl
    private val tealiumContext: TealiumContext
    private val disposables: CompositeDisposable = DisposableContainer()
    private val connectivityRetriever: ConnectivityRetriever

    init {
        makeTealiumDirectory(config)

        try {
            val db = dbProvider.database
        } catch (e: Exception) {
            throw PersistenceException("Database Initialization failed.", e)
        }

        val modulesRepository = SQLModulesRepository(dbProvider)
        val storage = ModuleStoreProviderImpl(dbProvider, modulesRepository)
        val sharedDataStore = storage.getSharedDataStore()

        settings =
            Observables.stateSubject(SettingsManager.loadInitialSettings(config, sharedDataStore))
        coreSettings =
            settings.map(SdkSettings::coreSettings).withState(settings.value::coreSettings)

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

        connectivityRetriever =
            ConnectivityRetriever(config.application, tealiumScheduler, logger = logger)
        connectivityRetriever.subscribe()
        networkUtilities = createNetworkUtilities(logger, schedulers, connectivityRetriever)

        val settingsManager = SettingsManager(
            config,
            networkUtilities.networkHelper,
            sharedDataStore,
            logger,
        )

        val transformerCoordinator =
            createTransformationsCoordinator(config, coreSettings, schedulers)
        val barrierCoordinator =
            createBarrierCoordinator(config, connectivityRetriever.onConnectionStatusUpdated,  coreSettings)

        val queueRepository = SQLQueueRepository(
            dbProvider,
            coreSettings.value.maxQueueSize,
            TimeFrame(coreSettings.value.expiration.toLong(), TimeUnit.DAYS)
        )
        val queueManager = createQueueManager(queueRepository, coreSettings, moduleManager.modules)

        dispatchManager = DispatchManagerImpl(
            moduleManager = moduleManager,
            barrierCoordinator = barrierCoordinator,
            transformerCoordinator = transformerCoordinator,
            dispatchers = moduleManager.modules
                .map { it.filterIsInstance(Dispatcher::class.java).toSet() },
            queueManager = queueManager,
            logger = logger
        )
        tracker = TrackerImpl(moduleManager, dispatchManager, logger)

        val visitorIdProvider = VisitorIdProviderImpl(
            config,
            sharedDataStore,
            logger
        )
        subscribeIdentityUpdates(
            settings.map(SdkSettings::coreSettings),
            storage.getModuleStore(DataLayerImpl),
            visitorIdProvider,
        ).addTo(disposables)

        tealiumContext =
            TealiumContext(
                config.application,
                config,
                logger = logger,
                // TODO - Visitor Storage not done yet, but EventStream requires a value for tealium_visitor_id
                visitorId = visitorIdProvider.visitorId,
                storageProvider = ModuleStoreProviderImpl(
                    dbProvider, modulesRepository
                ),
                network = networkUtilities,
                coreSettings = coreSettings,
                tracker = tracker,
                schedulers = schedulers,
                activityManager = activityManager,
                transformerRegistry = TransformerRegistryImpl(transformerCoordinator),
                barrierRegistry = BarrierRegistryImpl(barrierCoordinator),
                moduleManager = moduleManager
            )

        moduleManager.addModuleFactory(InternalModuleFactories.consentModuleFactory(queueManager))
        settings.subscribe { newSettings ->
            moduleManager.updateModuleSettings(tealiumContext, newSettings)
        }.addTo(disposables)

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
        connectivityRetriever.unsubscribe()
    }

    companion object {

        fun getDefaultModules(): List<ModuleFactory> {
            return listOf(
                TealiumCollector,
                DataLayerImpl,
                TraceManagerImpl,
                DeeplinkManagerImpl,
                TimedEventsManagerImpl
            )
        }

        fun createNetworkUtilities(
            logger: Logger,
            schedulers: Schedulers,
            connectivity: Connectivity
        ): NetworkUtilities {
            val networkClient = HttpClient(
                logger = logger,
                schedulers.tealium,
                schedulers.io,
                interceptors = mutableListOf(ConnectivityInterceptor(connectivity))
            )
            return NetworkUtilities(
                connectivity = connectivity,
                networkClient = networkClient,
                networkHelper = NetworkHelperImpl(networkClient, logger)
            )
        }

        private fun getDefaultBarriers(): Observable<Set<ScopedBarrier>> {
            return Observables.just(
                setOf(
                    ScopedBarrier(
                        ConnectivityBarrier.BARRIER_ID,
                        setOf(BarrierScope.Dispatcher(CollectDispatcher.moduleName))
                    )
                )
            )
        }

        fun createBarrierCoordinator(
            config: TealiumConfig,
            connectionStatus: Observable<Connectivity.Status>,
            coreSettings: Observable<CoreSettings>
        ): BarrierCoordinator {
            return BarrierCoordinatorImpl(
                config.barriers + ConnectivityBarrier(connectionStatus),
                coreSettings.combine(getDefaultBarriers()) { settings, scopedBarriers ->
                    (settings.barriers + scopedBarriers).toSet()
                }
            )
        }

        fun createTransformationsCoordinator(
            config: TealiumConfig,
            coreSettings: ObservableState<CoreSettings>,
            schedulers: Schedulers
        ): TransformerCoordinator {
            return TransformerCoordinatorImpl(
                config.transformers,
                coreSettings.map(CoreSettings::transformations)
                    .withState(coreSettings.value::transformations),
                schedulers.tealium
            )
        }

        fun createQueueManager(
            queueRepository: QueueRepository,
            coreSettings: Observable<CoreSettings>,
            allModules: ObservableState<Set<Module>>
        ): QueueManager {
            return QueueManagerImpl(
                queueRepository,
                coreSettings,
                allModules.filter { modules -> modules.isNotEmpty() }
                    .map { modules -> modules.filter { module -> module is Dispatcher || module is ConsentModule } }
                    .map { processors -> processors.map { it.name }.toSet() }
            )
        }

        fun makeTealiumDirectory(config: TealiumConfig) {
            val pathName =
                "${config.application.filesDir}${File.separatorChar}tealium${File.separatorChar}${config.accountName}${File.separatorChar}${config.profileName}${File.separatorChar}${config.environment.environment}"
            val tealiumDirectory = File(pathName)
            if (tealiumDirectory.exists()) return

            try {
                tealiumDirectory.mkdirs()
            } catch (e: IOException) {
                throw PersistenceException("Failed to create Tealium directory", e)
            }
        }
    }
}