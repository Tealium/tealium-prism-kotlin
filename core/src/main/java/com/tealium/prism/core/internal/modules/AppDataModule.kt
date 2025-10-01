package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.settings.modules.AppDataSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.Dispatch.Keys.APP_UUID
import com.tealium.prism.core.api.tracking.DispatchContext

/**
 * Collects data related to the application package.
 */
class AppDataModule(
    private val appDataProvider: AppDataProvider
) : Collector {

    constructor(tealiumContext: TealiumContext, dataStore: DataStore) : this(AppDataProviderImpl(
        tealiumContext.context,
        dataStore
    ))

    private val baseData = DataObject.create {
        put(APP_UUID, appDataProvider.appUuid)
        put(Dispatch.Keys.APP_RDNS, appDataProvider.appRdns)
        put(Dispatch.Keys.APP_NAME, appDataProvider.appName)
        put(Dispatch.Keys.APP_BUILD, appDataProvider.appBuild)
        put(Dispatch.Keys.APP_VERSION, appDataProvider.appVersion)
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        return baseData.copy {
            put(Dispatch.Keys.APP_MEMORY_USAGE, appDataProvider.appMemoryUsage)
        }
    }

    override val id: String = Modules.Types.APP_DATA
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    class Factory(
        settings: DataObject? = null
    ) : ModuleFactory {

        private val enforcedSettings: List<DataObject> =
            settings?.let { listOf(it) } ?: emptyList()

        constructor(settingsBuilder: AppDataSettingsBuilder) : this(settingsBuilder.build())

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override val moduleType: String = Modules.Types.APP_DATA

        override fun create(moduleId: String, context: TealiumContext, configuration: DataObject): Module? {
            val dataStore = context.storageProvider.getModuleStore(moduleId)
            return AppDataModule(context, dataStore)
        }
    }
}