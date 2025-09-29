package com.tealium.core.api.persistence

import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory

/**
 * The [ModuleStoreProvider] is responsible for registering and returning the [DataStore] instances
 * required by individual modules
 *
 */
interface ModuleStoreProvider {

    /**
     * Registers a [Module] for storage and returns its [DataStore] object, which can
     * be used to read/write data.
     *
     * @param module The module whose [DataStore] is required.
     */
    fun getModuleStore(moduleId: String): DataStore

    /**
     * Registers a [Module] for storage and returns its [DataStore] object, which can
     * be used to read/write data.
     *
     * @param module The module whose [DataStore] is required.
     */
    fun getModuleStore(module: Module): DataStore

    /**
     * Returns a [DataStore] that is not tied to a specific module, and therefore available for
     * shared storage.
     * Be mindful that this store can therefore be read from and written to by any code with access
     * to it.
     *
     * It is also required to execute updates on the Tealium processing Thread.
     */
    fun getSharedDataStore(): DataStore
}