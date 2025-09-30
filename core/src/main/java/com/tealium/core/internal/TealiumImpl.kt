package com.tealium.core.internal

import com.tealium.core.api.Modules
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.logIfWarnEnabled
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
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.api.transform.Transformer
import com.tealium.core.internal.consent.ConsentIntegrationManager
import com.tealium.core.internal.dispatch.BarrierCoordinator
import com.tealium.core.internal.dispatch.BarrierCoordinatorImpl
import com.tealium.core.internal.dispatch.BarrierManager
import com.tealium.core.internal.dispatch.BarrierRegistryImpl
import com.tealium.core.internal.dispatch.DispatchManagerImpl
import com.tealium.core.internal.dispatch.MappingOperation
import com.tealium.core.internal.dispatch.MappingsEngine
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.internal.dispatch.QueueManagerImpl
import com.tealium.core.internal.dispatch.QueueMetricsImpl
import com.tealium.core.internal.dispatch.TransformerCoordinator
import com.tealium.core.internal.dispatch.TransformerCoordinatorImpl
import com.tealium.core.internal.dispatch.TransformerRegistryImpl
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.logger.LoggerImpl
import com.tealium.core.internal.misc.ActivityManagerImpl
import com.tealium.core.internal.misc.ActivityManagerProxy
import com.tealium.core.internal.misc.TrackerImpl
import com.tealium.core.internal.modules.InternalModuleManager
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.TealiumDataModule
import com.tealium.core.internal.modules.datalayer.DataLayerModule
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
import com.tealium.core.internal.rules.LoadRuleEngineImpl
import com.tealium.core.internal.session.SessionManagerImpl
import com.tealium.core.internal.session.SessionRegistryImpl
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
        schedulers.tealium
    ),
    activityManager: ActivityManager = ActivityManagerImpl.getInstance(config.application),
) {
    private val sdkSettingsSubject: ReplaySubject<SdkSettings> = Observables.replaySubject(1)
    private val logLevel: Observable<LogLevel> = sdkSettingsSubject.map { it.core.logLevel }
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
    private val barrierCoordinator: BarrierCoordinator
    private val tracker: TrackerImpl
    private val tealiumContext: TealiumContext
    private val disposables: CompositeDisposable = DisposableContainer()
    private val connectivityRetriever: ConnectivityRetriever
    private val visitorIdProvider: VisitorIdProvider
    private val onModulesReady: ReplaySubject<Unit> = Observables.replaySubject(1)
    private val activityManager: ActivityManager = subscribeActivityManager(activityManager)
    private val sessionManager: SessionManagerImpl

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
            settings.map(SdkSettings::core).withState(settings.value::core)

        sessionManager =
            SessionManagerImpl(
                sessionTimeout = coreSettings.map { it.sessionTimeout }
                    .withState { coreSettings.value.sessionTimeout },
                dataStore = storage.getSharedDataStore(),
                scheduler = schedulers.tealium,
                modulesRepository = modulesRepository,
                logger = logger
            )

        val transformerCoordinator =
            createTransformationsCoordinator(
                moduleManager.modules,
                settingsManager.sdkSettings,
                schedulers,
                logger
            )
        val barrierManager = BarrierManager(settings)

        val queueRepository = SQLQueueRepository(
            dbProvider,
            coreSettings.value.maxQueueSize,
            coreSettings.value.expiration
        )
        val queueManager =
            createQueueManager(
                queueRepository,
                coreSettings,
                moduleManager.modules,
                config.cmpAdapter != null,
                logger
            )

        val queueMetrics = QueueMetricsImpl(queueManager)
        barrierCoordinator = BarrierCoordinatorImpl(
            barrierManager.barriers,
            this.activityManager.applicationStatus,
            queueMetrics
        )

        val loadRuleEngine = LoadRuleEngineImpl(settings, logger)
        val mappingsEngine = MappingsEngine(
            settings.map(::extractMappings)
                .withState { extractMappings(settings.value) })
        val consentManager = ConsentIntegrationManager.create(
            moduleManager.modules,
            queueManager,
            config.cmpAdapter,
            settings.map { it.consent }.withState { settings.value.consent },
            schedulers.tealium,
            logger
        )

        dispatchManager = DispatchManagerImpl(
            moduleManager = moduleManager,
            barrierCoordinator = barrierCoordinator,
            transformerCoordinator = transformerCoordinator,
            queueManager = queueManager,
            loadRuleEngine = loadRuleEngine,
            mappingsEngine = mappingsEngine,
            consentManager = consentManager,
            logger = logger
        )
        tracker = TrackerImpl(
            moduleManager.modules,
            dispatchManager,
            loadRuleEngine,
            sessionManager,
            logger
        )

        visitorIdProvider = VisitorIdProviderImpl(
            config,
            sharedDataStore,
            logger
        )
        subscribeIdentityUpdates(
            settings.map(SdkSettings::core),
            storage.getModuleStore(Modules.Types.DATA_LAYER),
            visitorIdProvider,
        ).addTo(disposables)

        tealiumContext =
            TealiumContext(
                config.application,
                config,
                logger = logger,
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
                barrierRegistry = BarrierRegistryImpl(barrierManager),
                moduleManager = moduleManager,
                queueMetrics = queueMetrics,
                sessionRegistry = SessionRegistryImpl(sessionManager)
            )

        loadModuleFactories(config.modules, moduleManager, logger)

        barrierManager.initializeBarriers(config.barriers, tealiumContext)
        settings.subscribe { newSettings ->
            moduleManager.updateModuleSettings(tealiumContext, newSettings)
        }.addTo(disposables)
        onModulesReady.onNext(Unit)

        settingsManager.subscribeToActivityUpdates(activityManager.applicationStatus)
            .addTo(disposables)

        dispatchManager.startDispatchLoop()

        logger.info(LogCategory.TEALIUM, "Instance %s initialized.", instanceName)
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
            tracker.track(copy, DispatchContext.Source.application(), onComplete)
        }
    }

    fun flushEventQueue() {
        barrierCoordinator.flush()
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
        sessionManager.shutdown()
    }

    companion object {

        private fun getDefaultModules(): List<ModuleFactory> {
            return listOf(
                DataLayerModule.Factory(),
                TealiumDataModule.Factory(),
            )
        }

        /**
         * Loads the given [factories] into the given [moduleManager].
         *
         * Before adding them, it will also:
         *  - add the required default module factory implementations if they are missing
         *  - log warnings when duplicate factory moduleType's are provided
         *  - log warnings when a factory with a default moduleType is provided, but the implementation is not ours
         *
         *  @param factories The [ModuleFactory]s to load into the [moduleManager]
         *  @param moduleManager The [InternalModuleManager] to add the [factories] to
         *  @param logger The [Logger] to log any issues to
         */
        fun loadModuleFactories(
            factories: List<ModuleFactory>,
            moduleManager: InternalModuleManager,
            logger: Logger
        ) {
            val defaults = getDefaultModules()

            for (validatedFactory in addAndValidateDefaultFactories(factories, defaults, logger)) {
                if (!moduleManager.addModuleFactory(validatedFactory)) {
                    logger.logIfWarnEnabled(LogCategory.TEALIUM) {
                        "Duplicate Module Factory with moduleType \"${validatedFactory.moduleType}\" was found. It will not be used."
                    }
                }
            }
        }

        /**
         * Adds any missing required [ModuleFactory] implementations, and logs warnings for custom
         * factory implementations for required factories
         *
         * @param factories The list of provided [ModuleFactory] implementations
         * @param defaults The set of default/required [ModuleFactory]s
         * @param logger The logger to report any warnings to
         *
         * @return The new list of [ModuleFactory]s that includes the required defaults
         */
        fun addAndValidateDefaultFactories(
            factories: List<ModuleFactory>,
            defaults: List<ModuleFactory>,
            logger: Logger
        ): List<ModuleFactory> {
            val factoriesMap = factories.associateBy(ModuleFactory::moduleType)
                .toMutableMap()

            for (default in defaults) {
                val configuredFactory = factoriesMap[default.moduleType]
                if (configuredFactory == null) {
                    factoriesMap[default.moduleType] = default
                } else if (configuredFactory.javaClass != default.javaClass) {
                    // note. could choose to throw here instead, to ensure required modules are our own
                    // implementations. There's no good reason for users to supply their own data layer
                    // or tealium collector implementation.
                    logger.logIfWarnEnabled(LogCategory.TEALIUM) {
                        "A non-standard ModuleFactory implementation has been provided for default module (${configuredFactory.moduleType}"
                    }
                }
            }

            return factoriesMap.values.toList()
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

        fun createTransformationsCoordinator(
            modules: ObservableState<List<Module>>,
            sdkSettings: ObservableState<SdkSettings>,
            schedulers: Schedulers,
            logger: Logger
        ): TransformerCoordinator {
            return TransformerCoordinatorImpl(
                modules.map { it.filterIsInstance<Transformer>() }
                    .withState { modules.value.filterIsInstance<Transformer>() },
                sdkSettings.map { it.transformations.values.toSet() }
                    .withState { sdkSettings.value.transformations.values.toSet() },
                schedulers.tealium,
                logger
            )
        }

        fun createQueueManager(
            queueRepository: QueueRepository,
            coreSettings: Observable<CoreSettings>,
            allModules: ObservableState<List<Module>>,
            addConsent: Boolean,
            logger: Logger
        ): QueueManager {
            val maybeConsent = if (addConsent) setOf(ConsentIntegrationManager.ID) else emptySet()
            return QueueManagerImpl(
                queueRepository,
                coreSettings,
                allModules.filter { modules -> modules.isNotEmpty() }
                    .map { modules -> modules.filterIsInstance<Dispatcher>() }
                    .map { processors ->
                        processors.map { it.id }.toSet() + maybeConsent
                    },
                logger = logger
            )
        }

        fun extractMappings(settings: SdkSettings): Map<String, List<MappingOperation>> =
            settings.modules.mapNotNull { (key, moduleSettings) ->
                if (moduleSettings.mappings != null) {
                    key to moduleSettings.mappings
                } else null
            }.toMap()

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