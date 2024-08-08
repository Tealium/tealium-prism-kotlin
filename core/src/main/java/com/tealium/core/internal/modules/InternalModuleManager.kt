package com.tealium.core.internal.modules

import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.internal.settings.SdkSettings

interface InternalModuleManager: ModuleManager {

    /**
     * Adds a [ModuleFactory] to the list of available factories
     */
    fun addModuleFactory(vararg moduleFactory: ModuleFactory)

    /**
     * Updates the modules based on the latest set of settings.
     */
    fun updateModuleSettings(context: TealiumContext, settings: SdkSettings)

    /**
     * Returns all [Module] implementations that implement or extend the given [Class].
     *
     * @param clazz The Class or Interface to look for
     */
    fun <T> getModulesOfType(clazz: Class<T>): List<T>

    /**
     * Returns the first [Module] implementation that implements or extends the given [Class].
     *
     * @param clazz The Class or Interface to match against
     */
    fun <T> getModuleOfType(clazz: Class<T>): T?
}