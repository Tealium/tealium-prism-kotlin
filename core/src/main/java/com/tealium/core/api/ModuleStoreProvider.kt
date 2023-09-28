package com.tealium.core.api

/**
 * The [ModuleStoreProvider] is responsible for registering and returning the [DataStore] instances
 * required by individual modules
 *
 */
interface ModuleStoreProvider {
    /**
     * Registers a [ModuleFactory] for storage and returns its [DataStore] object, which can
     * be used to read/write data.
     *
     * @param moduleFactory The factory whose [DataStore] is required. This needs to be the
     * Factory, to support dependency injection into the [Module][com.tealium.core.api.Module]
     * itself.
     */
    fun getModuleStore(moduleFactory: ModuleFactory): DataStore

    /**
     * Registers a [Module] for storage and returns its [DataStore] object, which can
     * be used to read/write data.
     *
     * @param module The module whose [DataStore] is required.
     */
    fun getModuleStore(module: Module): DataStore
}