package com.tealium.core.internal.persistence

import com.tealium.core.api.DataStore
import com.tealium.core.api.Module

interface DataStoreFactory {
    fun getDataStore(module: Module): DataStore
}

internal class DataStoreFactoryImpl(
    private val dbProvider: DatabaseProvider,
    private val moduleRepository: ModuleStorageRepository
) : DataStoreFactory {

    override fun getDataStore(module: Module): DataStore {
        val moduleId = moduleRepository.modules[module.name] ?: moduleRepository.registerModule(module.name)
        if (moduleId < 0)
            throw Exception("Could not register module ${module.name} for storage")

        return DataStoreImpl(
            PersistentDatabaseStorage(
                dbProvider,
                moduleId
            )
        )
    }
}