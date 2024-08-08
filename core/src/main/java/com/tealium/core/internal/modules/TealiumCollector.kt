package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.api.data.TealiumBundle
import java.security.SecureRandom
import java.util.*
import kotlin.math.abs

class TealiumCollector(
    private val context: TealiumContext
): Module, Collector {

    private val secureRandom = SecureRandom()
    private val random: String
        get() {
            val rand = secureRandom.nextLong() % 10000000000000000L
            return String.format(Locale.ROOT, "%016d", abs(rand));
        }

    private val baseData = TealiumBundle.create {
        put(Dispatch.Keys.TEALIUM_ACCOUNT, context.config.accountName)
        put(Dispatch.Keys.TEALIUM_PROFILE, context.config.profileName)
        put(Dispatch.Keys.TEALIUM_ENVIRONMENT, context.config.environment)
        context.config.datasource?.let {
            put(Dispatch.Keys.TEALIUM_ACCOUNT, it)
        }
        put(Dispatch.Keys.TEALIUM_LIBRARY_NAME, BuildConfig.TEALIUM_LIBRARY_NAME)
        put(Dispatch.Keys.TEALIUM_LIBRARY_VERSION, BuildConfig.TEALIUM_LIBRARY_VERSION)
    }

    override fun collect(): TealiumBundle {
        return baseData.copy {
            put(Dispatch.Keys.TEALIUM_RANDOM, random)
            put(Dispatch.Keys.TEALIUM_VISITOR_ID, context.visitorId.value)
        }
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object: ModuleFactory {
        private const val moduleName = "TealiumCollector"
        override val name = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
            return TealiumCollector(context)
        }
    }
}