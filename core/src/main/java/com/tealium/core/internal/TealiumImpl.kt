package com.tealium.core.internal

import com.tealium.core.BuildConfig
import com.tealium.core.Environment
import com.tealium.core.LogLevel
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.*
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.TealiumCollector
import com.tealium.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.FileDatabaseProvider
import com.tealium.core.internal.persistence.ModulesRepository
import com.tealium.core.internal.persistence.SQLModulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class TealiumImpl(
    private val config: TealiumConfig,
    private val onReady: Tealium.OnTealiumReady? = null,
    private val dbProvider: DatabaseProvider = FileDatabaseProvider(config),
    private val backgroundService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : Tealium {

    private val tealiumScope: CoroutineScope =
        CoroutineScope(backgroundService.asCoroutineDispatcher())

    // TODO read level from file/config
    private val logger: Logger = Logger(logLevel = LogLevel.DEV)

    init {
        makeTealiumDirectory(config).let { success ->
            if (!success) {
                logger.error(BuildConfig.TAG, "Failed to create Tealium directory")
            }
        }
    }

    //
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

    init {
        backgroundService.submit {
            bootstrap()
        }
    }

    private fun bootstrap() {
        logger.debug(BuildConfig.TAG, "Initializing Database.")
        try {
            val db = dbProvider.database
            logger.debug(BuildConfig.TAG, "Database Initialized (v${db.version})")
        } catch (e: Exception) {
            logger.error(BuildConfig.TAG, "Database Initialization failed. ${e.message}")

            // Database failed to open.
            onReady?.onReady(this, e)
            return
        }

        val modulesRepository =
            SQLModulesRepository(dbProvider, tealiumScope = tealiumScope)

        val tealiumContext =
            TealiumContext(
                config.application,
                // TODO - read from file instead.
                coreSettings = CoreSettingsImpl("tealiummobile", "demo", Environment.DEV),
                dataLayer = dataLayer,
                logger = logger,
                visitorId = "", // TODO
                storageProvider = ModuleStoreProviderImpl(
                    dbProvider, modulesRepository
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

            // Validation
            // todo

//        if (false) { // TODO - if queued

//        } else if (false) { // TODO - if dropped

//        }

            // Dispatch
            // TODO - this might have been queued/batched.
            val dispatches = listOf(dispatch)
            _moduleManager.getModulesOfType(Dispatcher::class.java).forEach { dispatcher ->
                dispatcher.dispatch(
                    dispatches,
                )
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