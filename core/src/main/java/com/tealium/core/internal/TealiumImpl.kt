package com.tealium.core.internal

import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.misc.Schedulers
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.api.persistence.PersistenceException
import com.tealium.core.api.pubsub.CompositeDisposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.ReplaySubject
import com.tealium.core.api.settings.CoreSettings
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.internal.dispatch.BarrierCoordinator
import com.tealium.core.internal.dispatch.BarrierCoordinatorImpl
import com.tealium.core.internal.dispatch.BarrierRegistryImpl
import com.tealium.core.internal.dispatch.DispatchManagerImpl
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.internal.dispatch.QueueManagerImpl
import com.tealium.core.internal.dispatch.TransformerCoordinator
import com.tealium.core.internal.dispatch.TransformerCoordinatorImpl
import com.tealium.core.internal.dispatch.TransformerRegistryImpl
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.logger.LoggerImpl
import com.tealium.core.internal.misc.ActivityManagerImpl
import com.tealium.core.internal.misc.ActivityManagerProxy
import com.tealium.core.internal.misc.TrackerImpl
import com.tealium.core.internal.modules.DeeplinkManagerImpl
import com.tealium.core.internal.modules.InternalModuleManager
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.TealiumCollector
import com.tealium.core.internal.modules.TimedEventsManagerImpl
import com.tealium.core.internal.modules.collect.CollectDispatcher
import com.tealium.core.internal.modules.consent.ConsentModule
import com.tealium.core.internal.modules.datalayer.DataLayerModule
import com.tealium.core.internal.modules.trace.TraceManagerModule
import com.tealium.core.internal.network.ConnectivityBarrier
import com.tealium.core.internal.network.ConnectivityInterceptor
import com.tealium.core.internal.network.ConnectivityRetriever
import com.tealium.core.internal.network.HttpClient
import com.tealium.core.internal.network.NetworkHelperImpl
import com.tealium.core.internal.persistence.IdentityUpdatedObserver.subscribeIdentityUpdates
import com.tealium.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.core.internal.persistence.VisitorIdProvider
import com.tealium.core.internal.persistence.VisitorIdProviderImpl
import com.tealium.core.internal.persistence.database.DatabaseProvider
import com.tealium.core.internal.persistence.database.FileDatabaseProvider
import com.tealium.core.internal.persistence.repositories.ModulesRepository
import com.tealium.core.internal.persistence.repositories.QueueRepository
import com.tealium.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.core.internal.persistence.repositories.SQLQueueRepository
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo
import com.tealium.core.internal.settings.CoreSettingsImpl
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.core.internal.settings.SettingsManager
import java.io.File
import java.io.IOException

class TealiumImpl(
    private val config: TealiumConfig,
    private val schedulers: Schedulers,
    private val dbProvider: DatabaseProvider = FileDatabaseProvider(config),
    val moduleManager: InternalModuleManager = ModuleManagerImpl(
        emptyList(),
        schedulers.tealium
    ),
    activityManager: ActivityManager = ActivityManagerImpl.getInstance(config.application),
) {
    private val sdkSettingsSubject: ReplaySubject<SdkSettings> = Observables.replaySubject(1)
    private val logLevel: Observable<LogLevel> = sdkSettingsSubject.map { it.coreSettings.logLevel }
    val logger: Logger = LoggerImpl(
        config.logHandler,
        logLevel,
        config.enforcedSdkSettings.getDataObject(CoreSettingsImpl.MODULE_NAME)
            ?.get(CoreSettingsImpl.KEY_LOG_LEVEL, LogLevel.Converter)
    )
    private val instanceName: String = "${config.accountName}-${config.profileName}"
    private val settings: ObservableState<SdkSettings>
    private val coreSettings: ObservableState<CoreSettings>
    private val networkUtilities: NetworkUtilities
    private val dispatchManager: DispatchManagerImpl
    private val tracker: TrackerImpl
    private val tealiumContext: TealiumContext
    private val disposables: CompositeDisposable = DisposableContainer()
    private val connectivityRetriever: ConnectivityRetriever
    private val visitorIdProvider: VisitorIdProvider
    private val onModulesReady: ReplaySubject<Unit> = Observables.replaySubject(1)
    private val activityManager: ActivityManager = subscribeActivityManager(activityManager)

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

        // TODO - clear session data if necessary
        logger.debug(LogCategory.TEALIUM, "Purging expired data from the database")
        modulesRepository.deleteExpired(ModulesRepository.ExpirationType.UntilRestart)

        connectivityRetriever =
            ConnectivityRetriever(config.application, schedulers.tealium, logger = logger)
        connectivityRetriever.subscribe()
        networkUtilities = createNetworkUtilities(logger, schedulers, connectivityRetriever)

        val settingsManager = SettingsManager(
            config,
            networkUtilities.networkHelper,
            sharedDataStore,
            logger = logger
        )

        settings = settingsManager.sdkSettings
        settings.subscribe(sdkSettingsSubject)

        coreSettings =
            settings.map(SdkSettings::coreSettings).withState(settings.value::coreSettings)

        val transformerCoordinator =
            createTransformationsCoordinator(config, coreSettings, schedulers)
        val barrierCoordinator =
            createBarrierCoordinator(
                config,
                connectivityRetriever.onConnectionStatusUpdated,
                coreSettings
            )

        val queueRepository = SQLQueueRepository(
            dbProvider,
            coreSettings.value.maxQueueSize,
            coreSettings.value.expiration
        )
        val queueManager =
            createQueueManager(queueRepository, coreSettings, moduleManager.modules, logger)

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

        visitorIdProvider = VisitorIdProviderImpl(
            config,
            sharedDataStore,
            logger
        )
        subscribeIdentityUpdates(
            settings.map(SdkSettings::coreSettings),
            storage.getModuleStore(DataLayerModule),
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
                activityManager = this.activityManager,
                transformerRegistry = TransformerRegistryImpl(transformerCoordinator),
                barrierRegistry = BarrierRegistryImpl(barrierCoordinator),
                moduleManager = moduleManager
            )

        moduleManager.addModuleFactory(*getDefaultModules(moduleManager.modules).toTypedArray())
        val factories = config.modules.map { factory ->
            // Consent is a special case that should be added externally, but needs internal
            // components that should not be exposed anywhere else.
            if (factory is ConsentModule.Factory) {
                factory.copy(queueManager = queueManager, modules = moduleManager.modules)
            } else factory
        }
        moduleManager.addModuleFactory(*factories.toTypedArray())

        settings.subscribe { newSettings ->
            moduleManager.updateModuleSettings(tealiumContext, newSettings)
        }.addTo(disposables)
        onModulesReady.onNext(Unit)

        settingsManager.subscribeToActivityUpdates(activityManager.applicationStatus)
            .addTo(disposables)

        dispatchManager.startDispatchLoop()

        logger.info(LogCategory.TEALIUM, "Instance %s initialized.", instanceName)
        // todo - might have queued incoming events + dispatch them now.
    }

    /**
     * The main [ActivityManagerImpl] publishes updates on the Main scheduler. Allowing internal
     * components to simply subscribe/observe on the tealium scheduler would mean that they each would
     * receive their notifications in a separate [Runnable].
     *
     * This method sets up a new ActivityManager for use within this Tealium instance, to
     * publish each update to all components in one go.
     *
     * It also schedules the activity/application notifications to be sent once the TealiumImpl
     * is finished loading. This could potentially be moved into the TealiumImpl instead, after the
     * Modules have finished loading.
     */
    private fun subscribeActivityManager(
        activityManager: ActivityManager
    ): ActivityManager {
        val activitySubject = Observables.publishSubject<ActivityManager.ActivityStatus>()
        val appSubject = Observables.replaySubject<ActivityManager.ApplicationStatus>(1)
        val activityManagerProxy = ActivityManagerProxy(activitySubject, appSubject)

        activityManager.applicationStatus.observeOn(schedulers.tealium)
            .combine(onModulesReady) { status, _ ->
                status
            }
            .subscribe(appSubject)
            .addTo(disposables)

        activityManager.activities.observeOn(schedulers.tealium)
            .combine(onModulesReady) { status, _ ->
                status
            }
            .subscribe(activitySubject)
            .addTo(disposables)

        return activityManagerProxy
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

    @Throws(PersistenceException::class)
    fun resetVisitorId(): String = visitorIdProvider.resetVisitorId()

    @Throws(PersistenceException::class)
    fun clearStoredVisitorIds(): String = visitorIdProvider.clearStoredVisitorIds()

    fun shutdown() {
        logger.info(LogCategory.TEALIUM, "Instance %s shutting down.", instanceName)

        disposables.dispose()
        dispatchManager.stopDispatchLoop()
        moduleManager.shutdown()
        connectivityRetriever.unsubscribe()
    }

    companion object {

        fun getDefaultModules(modules: ObservableState<Set<Module>>): List<ModuleFactory> {
            return listOf(
                TealiumCollector.Factory(modules),
                DataLayerModule,
                TraceManagerModule.Factory,
                DeeplinkManagerImpl,
                TimedEventsManagerImpl
            )
        }

        fun createNetworkUtilities(
            logger: Logger,
            schedulers: Schedulers,
            connectivity: Connectivity,
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
                networkHelper = NetworkHelperImpl(networkClient, logger),
                logger = logger
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
            allModules: ObservableState<Set<Module>>,
            logger: Logger
        ): QueueManager {
            return QueueManagerImpl(
                queueRepository,
                coreSettings,
                allModules.filter { modules -> modules.isNotEmpty() }
                    .map { modules -> modules.filter { module -> module is Dispatcher || module is ConsentModule } }
                    .map { processors -> processors.map { it.id }.toSet() },
                logger = logger
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