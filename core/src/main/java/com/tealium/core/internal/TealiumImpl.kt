package com.tealium.core.internal

import com.tealium.core.BuildConfig
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.*
import com.tealium.core.api.ConsentManager
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.internal.dispatch.BarrierCoordinatorImpl
import com.tealium.core.internal.dispatch.DispatchManagerImpl
import com.tealium.core.internal.dispatch.TransformerCoordinatorImpl
import com.tealium.core.internal.dispatch.VolatileQueueManagerImpl
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.TealiumCollector
import com.tealium.core.internal.network.ConnectivityInterceptor
import com.tealium.core.internal.network.ConnectivityRetriever
import com.tealium.core.internal.network.HttpClient
import com.tealium.core.internal.network.NetworkHelperImpl
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.FileDatabaseProvider
import com.tealium.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.core.internal.persistence.ModulesRepository
import com.tealium.core.internal.persistence.SQLModulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class TealiumImpl(
    private val config: TealiumConfig,
    private val onReady: Tealium.OnTealiumReady? = null,
    private val dbProvider: DatabaseProvider = FileDatabaseProvider(config),
    private val backgroundService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val networkService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : Tealium {

    private val tealiumScope: CoroutineScope =
        CoroutineScope(backgroundService.asCoroutineDispatcher())

    // SettingsRetriever to get settings

    // TODO read level from file/config
    private val logger: Logger =
        LoggerImpl(logHandler = config.overrideLogHandler ?: LoggerImpl.ConsoleLogHandler)

    private val networkClient: NetworkClient =
        HttpClient(
            logger = logger,
            backgroundService,
            networkService,
            interceptors = mutableListOf(ConnectivityInterceptor.getInstance(config.application))
        )

    init {
        makeTealiumDirectory(config).let { success ->
            if (!success) {
                logger.error?.log(BuildConfig.TAG, "Failed to create Tealium directory")
            }
        }
    }

    private var _moduleManager: ModuleManager = object : ModuleManager {
        override fun <T> getModulesOfType(clazz: Class<T>): List<T> {
            return emptyList()
        }

        override fun <T> getModuleOfType(clazz: Class<T>): T? {
            return null
        }

        override fun <T> getModuleOfType(clazz: Class<T>, block: (T?) -> Unit) {
            block(null)
        }
    }

    // TODO - Update ModuleManager
    override val modules: ModuleManager
        get() = _moduleManager

    private lateinit var _dispatchManager: DispatchManagerImpl

    init {
        backgroundService.submit {
            bootstrap()
        }
    }

    private fun bootstrap() {
        logger.debug?.log(BuildConfig.TAG, "Initializing Database.")
        try {
            val db = dbProvider.database
            logger.debug?.log(BuildConfig.TAG, "Database Initialized (v${db.version})")
        } catch (e: Exception) {
            logger.error?.log(BuildConfig.TAG, "Database Initialization failed. ${e.message}")

            // Database failed to open.
            onReady?.onReady(this, e)
            return
        }

        val modulesRepository =
            SQLModulesRepository(dbProvider, tealiumScope = tealiumScope)

        val tealiumContext: TealiumContext =
            TealiumContext(
                config.application,
                // TODO - read from file instead.
                config = config,
                dataLayer = dataLayer,
                logger = logger,
                // TODO - Visitor Storage not done yet, but EventStream requires a value for tealium_visitor_id
                visitorId = UUID.randomUUID().toString().replace("-", ""),
                storageProvider = ModuleStoreProviderImpl(
                    dbProvider, modulesRepository
                ),
                network = NetworkUtilities(
                    connectivity = ConnectivityRetriever.getInstance(config.application),
                    networkClient = networkClient,
                    networkHelper = NetworkHelperImpl(networkClient)
                ),
                tealium = this
            )


        // TODO - clear session data if necessary
        modulesRepository.deleteExpired(ModulesRepository.ExpirationType.UntilRestart)

        val modules: MutableList<ModuleFactory> = mutableListOf(
            TealiumCollector,
            DataLayerImpl,
            TraceManagerImpl,
            DeeplinkManagerImpl,
            TimedEventsManagerImpl,
            ConsentManagerImpl.Factory,
        )

        // TODO - needs registration to settings updates
        _moduleManager = ModuleManagerImpl(
            tealiumContext, SdkSettings(emptyMap()), modules + config.modules, tealiumScope
        )

        _dispatchManager = DispatchManagerImpl(
            consentManager = com.tealium.core.internal.consent.ConsentManagerImpl(),
            // TODO - Load default barriers
            barrierCoordinator = BarrierCoordinatorImpl(setOf(), setOf()),
            // TODO - load transformers
            transformerCoordinator = TransformerCoordinatorImpl(setOf(), MutableStateFlow(setOf())),
            // TODO - create flow from the ModulesManager
            dispatchers = MutableStateFlow(_moduleManager.getModulesOfType(Dispatcher::class.java).toSet()),
            // TODO - hook this up to persistent storage
            queueManager = VolatileQueueManagerImpl(scope = tealiumScope),
            tealiumScope = tealiumScope,
            logger = logger
        )
        _dispatchManager.startDispatchLoop()

        logger.debug?.log(BuildConfig.TAG, "Bootstrap complete, continue to onReady")
        // todo - might have queued incoming events + dispatch them now.
        onReady?.onReady(this, null)
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

    override val visitorService: VisitorService?
        get() = modules.getModuleOfType(VisitorService::class.java)

    @Suppress("NAME_SHADOWING")
    override fun track(dispatch: Dispatch) {
        tealiumScope.launch {

            Dispatch.create(dispatch).let { dispatch ->
                // collection
                val builder = TealiumBundle.Builder()
                _moduleManager.getModulesOfType(Collector::class.java).forEach {
                    builder.putAll(it.collect())
                }
                dispatch.addAll(builder.getBundle())

                // Transform
//                _moduleManager.getModulesOfType(Transformer::class.java).forEach {
//                    it.transform(dispatch)
//                }

                logger.debug?.log(
                    BuildConfig.TAG,
                    "Dispatch(${dispatch.id.substring(0, 5)}) Ready - ${dispatch.payload()}"
                )

                // Validation
                // todo

//        if (false) { // TODO - if queued

//        } else if (false) { // TODO - if dropped

//        }

                // Dispatch
                // TODO - this might have been queued/batched.
//            val dispatches = listOf(dispatch, dispatch, dispatch, dispatch, dispatch) // Testing.
                _dispatchManager.track(dispatch)
            }
        }
    }

    override fun flushEventQueue() {
        TODO("Not yet implemented")
    }

    fun shutdown() {
        TODO("Not yet implemented")
    }

    companion object {

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