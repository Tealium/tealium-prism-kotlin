package com.tealium.core.internal.persistence

import com.tealium.core.api.DataStore
import com.tealium.core.api.ModuleStoreProvider
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import kotlinx.coroutines.flow.mapNotNull

/**
 * This is the default implementation of [ModuleStoreProvider] for registering and returning
 * storage to be used by individual modules.
 *
 * @param dbProvider Database provider instance to provide a valid instance of a [android.database.sqlite.SQLiteDatabase]
 * @param moduleRepository The [ModulesRepository] to use to register the module for storage
 */
internal class ModuleStoreProviderImpl(
    private val dbProvider: DatabaseProvider,
    private val moduleRepository: ModulesRepository,
    // TODO - perhaps reconsider switching to a single `KeyValueRepository` instance with id's required on each method
    private val keyValueRepositoryCreator: (DatabaseProvider, Long) -> KeyValueRepository = { provider, moduleId ->
        SQLKeyValueRepository(provider, moduleId)
    },
    private val stores: MutableMap<Long, DataStore> = mutableMapOf()
) : ModuleStoreProvider {

    override fun getModuleStore(moduleFactory: ModuleFactory): DataStore {
        return getModuleStoreForName(moduleFactory.name)
    }

    override fun getModuleStore(module: Module): DataStore {
        return getModuleStoreForName(module.name)
    }

    override fun getSharedDataStore(): DataStore {
        return getModuleStoreForName(SHARED_STORE_NAME)
    }

    private fun getModuleStoreForName(name: String): DataStore {
        val moduleId =
            moduleRepository.modules[name] ?: moduleRepository.registerModule(name)
        if (moduleId < 0)
            throw Exception("Could not register module $name for storage")

        return stores[moduleId] ?: ModuleStore(
            keyValueRepository = keyValueRepositoryCreator.invoke(dbProvider, moduleId),
            onDataExpired = moduleRepository.onDataExpired
                .mapNotNull { it[moduleId] }
        ).also {
            stores[moduleId] = it
        }
    }

    companion object {
        const val SHARED_STORE_NAME = "shared"
    }
}