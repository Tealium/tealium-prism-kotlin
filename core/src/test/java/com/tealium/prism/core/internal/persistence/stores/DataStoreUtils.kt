package com.tealium.prism.core.internal.persistence.stores

import android.app.Application
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.tests.common.getDefaultConfig

fun getSharedDataStore(app: Application): DataStore {
    val dbProvider = InMemoryDatabaseProvider(getDefaultConfig(app))
    val modulesRepository = SQLModulesRepository(dbProvider)
    val moduleStoreProvider = ModuleStoreProviderImpl(dbProvider, modulesRepository)

    return moduleStoreProvider.getSharedDataStore()
}