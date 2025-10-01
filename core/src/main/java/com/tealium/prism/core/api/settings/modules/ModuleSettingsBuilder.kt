package com.tealium.prism.core.api.settings.modules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.internal.settings.ModuleSettings

/**
 * Base class to extend from when creating a settings builder for a new module.
 *
 * [Module] settings are expected to be [DataObject]s and as such, this class becomes a wrapper
 * around a standard [DataObject.Builder], but allows for implementations to provide more
 * user-friendly method names.
 *
 * Therefore it also doesn't require users to know which json key any particular setting needs to be set at.
 */
open class ModuleSettingsBuilder<T: ModuleSettingsBuilder<T>>(private val moduleType: String) {
    protected val moduleSettings: DataObject.Builder = DataObject.Builder()
    protected val configuration: DataObject.Builder = DataObject.Builder()

    /**
     * Protected implementation for more specific builders to use when enabling multiple module id
     * support.
     *
     * Specific builders that require this feature can implement [MultipleInstancesModuleSettingsBuilder]
     * and wire its [MultipleInstancesModuleSettingsBuilder.setModuleId] method to call [setModuleIdInternal]
     * to effectively make this functionality public.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun setModuleIdInternal(moduleId: String): T = apply {
        moduleSettings.put(ModuleSettings.KEY_MODULE_ID, moduleId)
    } as T

    /**
     * Sets the order in which the [Module] needs to be initialized.
     *
     * Modules will be initialized in natural integer order; lower values first.
     */
    @Suppress("UNCHECKED_CAST")
    fun setOrder(order: Int): T = apply {
        moduleSettings.put(ModuleSettings.KEY_ORDER, order)
    } as T

    /**
     * Sets the resulting [Module] to be permanently enabled/disabled. Local/Remote settings sources will
     * be overridden by this
     */
    @Suppress("UNCHECKED_CAST")
    fun setEnabled(enabled: Boolean): T = apply {
        moduleSettings.put(ModuleSettings.KEY_ENABLED, enabled)
    } as T

    /**
     * Returns the complete [Module] settings as configured by this [ModuleSettingsBuilder].
     */
    fun build(): DataObject =
        moduleSettings
            .put(ModuleSettings.KEY_MODULE_TYPE, moduleType)
            .put(ModuleSettings.KEY_CONFIGURATION, configuration.build())
            .build()
}