package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleInfo
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.abs

class TealiumCollector(
    private val config: TealiumConfig,
    private val visitorId: ObservableState<String>,
    private val moduleManager: ModuleManager
) : Module, Collector {

    constructor(
        context: TealiumContext,
    ) : this(context.config, context.visitorId, context.moduleManager)

    private val secureRandom = SecureRandom()
    private val random: String
        get() {
            val rand = secureRandom.nextLong() % 10000000000000000L
            return String.format(Locale.ROOT, "%016d", abs(rand));
        }

    private val baseData = DataObject.create {
        put(Dispatch.Keys.TEALIUM_ACCOUNT, config.accountName)
        put(Dispatch.Keys.TEALIUM_PROFILE, config.profileName)
        put(Dispatch.Keys.TEALIUM_ENVIRONMENT, config.environment)
        config.datasource?.let {
            put(Dispatch.Keys.TEALIUM_DATASOURCE_ID, it)
        }
        put(Dispatch.Keys.TEALIUM_LIBRARY_NAME, BuildConfig.TEALIUM_LIBRARY_NAME)
        put(Dispatch.Keys.TEALIUM_LIBRARY_VERSION, BuildConfig.TEALIUM_LIBRARY_VERSION)
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        return baseData.copy {
            put(Dispatch.Keys.TEALIUM_RANDOM, random)
            put(Dispatch.Keys.TEALIUM_VISITOR_ID, visitorId.value)

            put(
                Dispatch.Keys.ENABLED_MODULES,
                moduleManager.modulesInfo.map(ModuleInfo::id)
                    .asDataList()
            )
            put(
                Dispatch.Keys.ENABLED_MODULES_VERSIONS,
                moduleManager.modulesInfo.map(ModuleInfo::version)
                    .asDataList()
            )
        }
    }


    override val id: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object {
        private const val moduleName = "TealiumCollector"
    }

    object Factory : ModuleFactory {
        override val id = moduleName

        override fun create(context: TealiumContext, configuration: DataObject): Module? {
            return TealiumCollector(context)
        }
    }
}