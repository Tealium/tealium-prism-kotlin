package com.tealium.core.internal.persistence

import com.tealium.core.api.DataStore
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory

/**
 * The [DataStoreProvider] is responsible for registering and returning the [DataStore] instances
 * required by individual modules
 *
 */
interface DataStoreProvider {
    /**
     * Registers a [ModuleFactory] for storage and returns its [DataStore] object, which can
     * be used to read/write data.
     *
     * @param moduleFactory The factory whose [DataStore] is required. This needs to be the
     * Factory, to support dependency injection into the [Module][com.tealium.core.api.Module]
     * itself.
     */
    fun getDataStore(moduleFactory: ModuleFactory): DataStore

    /**
     * Registers a [Module] for storage and returns its [DataStore] object, which can
     * be used to read/write data.
     *
     * @param module The module whose [DataStore] is required.
     */
    fun getDataStore(module: Module): DataStore
}

/**
 * This is the default implementation of [DataStoreProvider] for registering and returning
 * storage to be used by individual modules.
 *
 * @param dbProvider Database provider instance to provide a valid instance of a [android.database.sqlite.SQLiteDatabase]
 * @param moduleRepository The [ModuleStorageRepository] to use to register the module for storage
 */
internal class DataStoreProviderImpl(
    private val dbProvider: DatabaseProvider,
    private val moduleRepository: ModuleStorageRepository
) : DataStoreProvider {

    override fun getDataStore(moduleFactory: ModuleFactory): DataStore {
        return getDataStoreForName(moduleFactory.name)
    }

    override fun getDataStore(module: Module): DataStore {
        return getDataStoreForName(module.name)
    }

    private fun getDataStoreForName(name: String): DataStore {
        val moduleId =
            moduleRepository.modules[name] ?: moduleRepository.registerModule(name)
        if (moduleId < 0)
            throw Exception("Could not register module $name for storage")

        return DataStoreImpl(
            SQLiteStorageStrategy(
                dbProvider,
                moduleId
            )
        )
    }
}