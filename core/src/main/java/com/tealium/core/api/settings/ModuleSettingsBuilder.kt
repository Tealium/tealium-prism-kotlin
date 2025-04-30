package com.tealium.core.api.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Module
import com.tealium.core.internal.settings.ModuleSettings

/**
 * Base class to extend from when creating a settings builder for a new module.
 *
 * [Module] settings are expected to be [DataObject]s and as such, this class becomes a wrapper
 * around a standard [DataObject.Builder], but allows for implementations to provide more
 * user-friendly method names.
 *
 * Therefore it also doesn't require users to know which json key any particular setting needs to be set at.
 */
open class ModuleSettingsBuilder {
    protected val builder: DataObject.Builder = DataObject.Builder()
    protected val configuration: DataObject.Builder = DataObject.Builder()

    /**
     * Sets the resulting [Module] to be permanently enabled/disabled. Local/Remote settings sources will
     * be overridden by this
     */
    fun setEnabled(enabled: Boolean) = apply {
        builder.put(ModuleSettings.KEY_ENABLED, enabled)
    }

    /**
     * Returns the complete [Module] settings as configured by this [ModuleSettingsBuilder].
     */
    fun build(): DataObject =
        builder
            .put(ModuleSettings.KEY_CONFIGURATION, configuration.build())
            .build()
}