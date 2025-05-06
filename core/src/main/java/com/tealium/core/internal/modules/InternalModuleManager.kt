package com.tealium.core.internal.modules

import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.internal.settings.SdkSettings

/**
 * The [InternalModuleManager] is an internal specialism of [ModuleManager] that provides
 * additional functionality. It is not intended for public use and thus should not
 * appear on any other public classes.
 *
 * It should first be loaded with all relevant [ModuleFactory]s by calling [addModuleFactory].
 * Calling [updateModuleSettings] will then create/update all modules based on the available
 * [ModuleFactory]s.
 */
interface InternalModuleManager : ModuleManager {

    /**
     * Observable stream of all [Module] implementations in the system.
     */
    val modules: ObservableState<List<Module>>

    /**
     * Adds a [ModuleFactory] to the list of available factories
     *
     * @param moduleFactory The [ModuleFactory] to be added
     *
     * @return true if the [moduleFactory] was added; else false
     */
    fun addModuleFactory(moduleFactory: ModuleFactory): Boolean

    /**
     * Updates the modules based on the latest set of settings.
     *
     * @param context The [TealiumContext] to provide to each module or factory
     * @param settings The latest set of [SdkSettings]
     */
    fun updateModuleSettings(context: TealiumContext, settings: SdkSettings)

    /**
     * Returns all [Module] implementations that implement or extend the given [Class].
     *
     * @param clazz The Class or Interface to look for
     */
    fun <T : Module> getModulesOfType(clazz: Class<T>): List<T>

    /**
     * Initiates the shutdown of all [Module] implementations and removes any references to them.
     *
     * Also removes all [ModuleFactory] references too.
     */
    fun shutdown()
}